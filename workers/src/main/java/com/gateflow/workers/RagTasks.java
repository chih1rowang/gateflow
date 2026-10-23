package com.gateflow.workers;

import com.netflix.conductor.sdk.workflow.task.InputParam;
import com.netflix.conductor.sdk.workflow.task.OutputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * rag_retriever 任务执行器（W4：双检索模式，可配置）。
 * - vector（基线）：问题向量化 → 向量库余弦相似度 TopK
 * - hybrid（优化）：向量 TopK + BM25 关键词 TopK → RRF 倒数排名融合
 *   BM25 用中文字符二元组（bigram）分词：无需分词器依赖，对精确术语/数字的字面匹配敏感——
 *   恰好补向量检索"字面不敏感"的短板（混合检索的动机见评测报告）。
 */
@Component
public class RagTasks {

    private static final Logger log = LoggerFactory.getLogger(RagTasks.class);
    private static final int RRF_K = 60;

    private final EmbeddingModel embeddingModel;
    private volatile EmbeddingStore<TextSegment> store;
    /** BM25 预建索引（reload 时随语料一起重建）：此前每次查询全量重算词频，语料变大后是瓶颈 */
    private volatile Bm25Index bm25Index;
    private final int maxResults;
    private final String docsPath;
    private final boolean hybrid;

    /** BM25 倒排统计：片段、每片段词频、词的文档频率、每片段长度、平均长度 */
    private record Bm25Index(List<TextSegment> segs, List<Map<String, Integer>> tfs,
                             Map<String, Integer> df, List<Integer> lens, double avgLen) {}

    /** Spring 注入与评测直构共用（@Value 带 defaultValue，评测侧可 new） */
    public RagTasks(@Value("${gateflow.rag.docs-path:./deploy/knowledge}") String docsPath,
                    @Value("${gateflow.rag.max-results:3}") int maxResults,
                    @Value("${gateflow.rag.hybrid:false}") boolean hybrid) {
        this.maxResults = maxResults;
        this.docsPath = docsPath;
        this.hybrid = hybrid;
        this.embeddingModel = new BgeSmallZhV15EmbeddingModel();
        reload();
    }

    /** 重建向量库与片段语料并原子替换（admin 端点调用；换语料免重启） */
    public synchronized int reload() {
        EmbeddingStore<TextSegment> newStore = new InMemoryEmbeddingStore<>();
        DocumentSplitter splitter = DocumentSplitters.recursive(300, 30);
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(newStore)
                .build();
        List<Document> documents = FileSystemDocumentLoader.loadDocuments(docsPath);
        // 空目录防护：入库器对空列表抛 IllegalArgument（边界实测发现），空语料直接保持空库
        if (!documents.isEmpty()) {
            ingestor.ingest(documents);
        }
        // 手动重切一份片段留作 BM25 语料（与入库同参数，保证两路索引一致），并预建 BM25 统计
        List<TextSegment> newSegments = new ArrayList<>();
        for (Document doc : documents) {
            newSegments.addAll(splitter.split(doc));
        }
        this.store = newStore;
        this.bm25Index = buildIndex(newSegments);
        log.info("RAG store ready: {} documents, {} segments, hybrid={} from {}",
                documents.size(), newSegments.size(), hybrid, docsPath);
        return documents.size();
    }

    private static Bm25Index buildIndex(List<TextSegment> corpus) {
        int n = corpus.size();
        List<Map<String, Integer>> tfs = new ArrayList<>(n);
        Map<String, Integer> df = new HashMap<>();
        List<Integer> lens = new ArrayList<>(n);
        for (TextSegment seg : corpus) {
            Map<String, Integer> tf = termFreq(seg.text());
            tfs.add(tf);
            lens.add(tf.values().stream().mapToInt(Integer::intValue).sum());
            for (String term : tf.keySet()) df.merge(term, 1, Integer::sum);
        }
        double avgLen = lens.stream().mapToInt(Integer::intValue).average().orElse(1);
        return new Bm25Index(corpus, tfs, df, lens, avgLen);
    }

    @WorkerTask("rag_retriever")
    public @OutputParam("chunks") List<String> retrieve(@InputParam("question") String question) {
        List<String> chunks = retrieveTop(question).stream().map(TextSegment::text).toList();
        log.info("rag_retriever({}): question='{}' -> {} chunks", hybrid ? "hybrid" : "vector", question, chunks.size());
        return chunks;
    }

    /** 带元数据的检索（评测与溯源用）：按模式走纯向量或混合 */
    public List<TextSegment> retrieveTop(String question) {
        // 空白问题防护：向量模型拒绝嵌入空文本（边界实测发现），空白直接返回空结果
        if (question == null || question.isBlank()) {
            return List.of();
        }
        if (!hybrid) {
            // store.search 最多返回 min(k, 库内片段总数) 条，天然不会越界
            // （此前 subList(0, maxResults) 在片段总数 < maxResults 时抛下标越界——审查报告中等 7）
            return vectorRank(question, maxResults);
        }
        // 混合：两路各取深度候选（50），RRF 融合后取 TopK
        List<TextSegment> byVector = vectorRank(question, 50);
        List<TextSegment> byBm25 = bm25Rank(question, 50);
        return rrfFuse(byVector, byBm25, maxResults);
    }

    private List<TextSegment> vectorRank(String question, int k) {
        Embedding qe = embeddingModel.embed(question).content();
        List<EmbeddingMatch<TextSegment>> matches = store
                .search(EmbeddingSearchRequest.builder().queryEmbedding(qe).maxResults(k).build())
                .matches();
        return matches.stream().map(EmbeddingMatch::embedded).toList();
    }

    /** BM25：k1=1.5, b=0.75；中文按字符二元组分词（统计取自 reload 时预建的索引） */
    private List<TextSegment> bm25Rank(String question, int k) {
        Bm25Index idx = this.bm25Index;
        int n = idx.segs().size();
        Map<String, Integer> qTf = termFreq(question);
        final double k1 = 1.5, b = 0.75;
        record Scored(double score, TextSegment seg) {}
        List<Scored> scored = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Map<String, Integer> tf = idx.tfs().get(i);
            int len = idx.lens().get(i);
            double score = 0;
            for (String q : qTf.keySet()) {
                Integer f = tf.get(q);
                if (f == null) continue;
                int dfv = idx.df().getOrDefault(q, 1);
                double idf = Math.log(1 + (n - dfv + 0.5) / (dfv + 0.5));
                score += idf * f * (k1 + 1) / (f + k1 * (1 - b + b * len / idx.avgLen()));
            }
            scored.add(new Scored(score, idx.segs().get(i)));
        }
        scored.sort((a, c) -> Double.compare(c.score(), a.score()));
        return scored.stream().limit(k).map(Scored::seg).toList();
    }

    /** RRF（Reciprocal Rank Fusion）：score = Σ 1/(k+rank)，k=60；免量纲、免调权。
     *  TextSegment 的 equals/hashCode 按"文本+元数据"值比较（langchain4j 1.22 字节码验证过），
     *  向量路与 BM25 路的不同实例能正确融合。static 包私有以便单测。 */
    static List<TextSegment> rrfFuse(List<TextSegment> a, List<TextSegment> b, int topK) {
        Map<TextSegment, Double> score = new HashMap<>();
        for (int i = 0; i < a.size(); i++) score.merge(a.get(i), 1.0 / (RRF_K + i + 1), Double::sum);
        for (int i = 0; i < b.size(); i++) score.merge(b.get(i), 1.0 / (RRF_K + i + 1), Double::sum);
        List<Map.Entry<TextSegment, Double>> sorted = new ArrayList<>(score.entrySet());
        sorted.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
        Set<TextSegment> seen = new LinkedHashSet<>();
        for (var e : sorted) {
            if (seen.size() >= topK) break;
            seen.add(e.getKey());
        }
        return new ArrayList<>(seen);
    }

    private static Map<String, Integer> termFreq(String text) {
        Map<String, Integer> tf = new HashMap<>();
        for (String g : bigrams(text)) tf.merge(g, 1, Integer::sum);
        return tf;
    }

    /** 中文字符二元组（英文/数字连续段整体作为一个词）。static 包私有以便单测。 */
    static List<String> bigrams(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder latin = new StringBuilder();
        List<String> chars = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c) && c < 0x2E80) {           // ASCII 词与数字
                latin.append(c);
            } else {
                if (latin.length() > 0) { chars.add(latin.toString()); latin.setLength(0); }
                if (!Character.isWhitespace(c)) chars.add(String.valueOf(c));
            }
        }
        if (latin.length() > 0) chars.add(latin.toString());
        for (int i = 0; i < chars.size() - 1; i++) out.add(chars.get(i) + chars.get(i + 1));
        if (chars.size() == 1) out.add(chars.get(0));
        return out;
    }
}
