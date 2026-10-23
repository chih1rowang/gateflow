package com.gateflow.workers;

import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 边界值集成测试：检索链路在极端语料下的行为（需要加载本地 BGE 向量模型，约 10 秒；
 * 类名以 IT 结尾，CI 的 surefire 不自动跑，本地：mvn test -Dtest=EdgeCasesIT）。
 * 回归点：纯向量模式在"片段总数少于 maxResults"时曾抛下标越界（审查报告中等 7）。
 */
class EdgeCasesIT {

    @TempDir
    Path tmp;

    @Test
    void tinyCorpusFewerSegmentsThanMaxResultsDoesNotThrow() throws Exception {
        Path doc = tmp.resolve("tiny.md");
        Files.writeString(doc, "报销单一笔上限三百元。");
        RagTasks rag = new RagTasks(tmp.toString(), 3, false);
        List<TextSegment> top = rag.retrieveTop("报销上限多少");
        assertNotNull(top);
        assertTrue(top.size() <= 3);
    }

    @Test
    void emptyCorpusReturnsEmptyWithoutThrowing() throws Exception {
        Path emptyDir = Files.createDirectories(tmp.resolve("empty"));
        RagTasks rag = new RagTasks(emptyDir.toString(), 3, false);
        assertTrue(rag.retrieveTop("任意问题").isEmpty());
    }

    @Test
    void hybridOnTinyCorpusDoesNotThrow() throws Exception {
        Path doc = tmp.resolve("tiny2.md");
        Files.writeString(doc, "发票抬头必须与社团注册名称一致，否则不予报销。");
        RagTasks rag = new RagTasks(tmp.toString(), 3, true);
        List<TextSegment> top = rag.retrieveTop("发票抬头怎么写");
        assertTrue(top.size() <= 3);
    }

    @Test
    void blankQueryDoesNotThrow() throws Exception {
        Path doc = tmp.resolve("tiny3.md");
        Files.writeString(doc, "活动结束后两周内提交报销材料。");
        RagTasks rag = new RagTasks(tmp.toString(), 3, true);
        assertNotNull(rag.retrieveTop(""));
        assertNotNull(rag.retrieveTop("。。。"));
    }
}
