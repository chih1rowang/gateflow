package com.gateflow.workers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

/**
 * W4 验收③：自建 100 条评测集，对比纯向量检索与混合检索（BM25+RRF）的 recall@3。
 * 运行：mvn test -Dtest=RecallEvalIT（工作目录=workers 模块；类名以 IT 结尾，CI 的 surefire 不自动跑）
 * 命中判据：Top3 片段的 metadata.file_name == 期望文档（我自己标注——局限如实承认）。
 */
class RecallEvalIT {

    private static final int TOP_K = 3;

    @Test
    void recallVectorVsHybrid() throws Exception {
        JsonNode evalSet = new ObjectMapper()
                .readTree(Paths.get("../deploy/eval/evalset.json").toFile());
        int total = evalSet.size();

        // 两个实例分别以两种模式构建（构造即完成入库，BGE 预热各约 10 秒）
        RagTasks vector = new RagTasks("../deploy/knowledge", TOP_K, false);
        RagTasks hybrid = new RagTasks("../deploy/knowledge", TOP_K, true);

        int hitV = 0, hitH = 0;
        for (JsonNode item : evalSet) {
            String q = item.get("q").asText();
            String expect = item.get("doc").asText();
            if (hit(vector.retrieveTop(q), expect)) hitV++;
            if (hit(hybrid.retrieveTop(q), expect)) hitH++;
        }
        double rv = 100.0 * hitV / total;
        double rh = 100.0 * hitH / total;
        System.out.printf("=== RECALL@3 (n=%d) ===%n", total);
        System.out.printf("vector-only : %d/%d = %.1f%%%n", hitV, total, rv);
        System.out.printf("hybrid(RRF) : %d/%d = %.1f%%%n", hitH, total, rh);
        System.out.printf("paired      : %.1f%% -> %.1f%%%n", rv, rh);
    }

    private boolean hit(List<TextSegment> top, String expectDoc) {
        for (TextSegment s : top) {
            String f = s.metadata().getString("file_name");
            if (f != null && f.endsWith(expectDoc)) return true;
        }
        return false;
    }
}
