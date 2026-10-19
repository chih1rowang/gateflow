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

import java.util.List;

/**
 * rag_retriever 任务执行器：从本地制度文档库中检索与问题最相关的片段。
 *
 * 启动时一次性完成"入库"：加载 deploy/knowledge/ 下全部文档 → 切分成段落片段
 * → 本地 BGE 中文向量模型把每个片段转成向量 → 存入内存向量库。
 * 每次任务只做"检索"：问题向量化 → 相似度 TopK。
 */
@Component
public class RagTasks {

    private static final Logger log = LoggerFactory.getLogger(RagTasks.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> store;
    private final int maxResults;

    public RagTasks(@Value("${gateflow.rag.docs-path}") String docsPath,
                    @Value("${gateflow.rag.max-results}") int maxResults) {
        this.maxResults = maxResults;
        // in-process 模型：ONNX 推理在本进程内运行，不联网、不收费
        this.embeddingModel = new BgeSmallZhV15EmbeddingModel();
        this.store = new InMemoryEmbeddingStore<>();

        // 入库管道：切分（每片约 300 token、重叠 30）→ 向量化 → 存库
        DocumentSplitter splitter = DocumentSplitters.recursive(300, 30);
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build();
        List<Document> documents = FileSystemDocumentLoader.loadDocuments(docsPath);
        ingestor.ingest(documents);
        log.info("RAG store ready: {} documents ingested from {}", documents.size(), docsPath);
    }

    @WorkerTask("rag_retriever")
    public @OutputParam("chunks") List<String> retrieve(@InputParam("question") String question) {
        Embedding questionEmbedding = embeddingModel.embed(question).content();
        List<EmbeddingMatch<TextSegment>> matches = store
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(questionEmbedding)
                        .maxResults(maxResults)
                        .build())
                .matches();
        List<String> chunks = matches.stream()
                .map(m -> m.embedded().text())
                .toList();
        log.info("rag_retriever: question='{}' -> {} chunks (top score {})",
                question, chunks.size(),
                matches.isEmpty() ? "n/a" : String.format("%.4f", matches.get(0).score()));
        return chunks;
    }
}
