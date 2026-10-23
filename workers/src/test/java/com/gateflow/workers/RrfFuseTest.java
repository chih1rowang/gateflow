package com.gateflow.workers;

import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 边界值测试：RRF 倒数排名融合——不依赖向量模型（TextSegment 是纯数据类），CI 可跑。
 * 关键回归点：两路的同内容片段（不同实例）必须融合到同一个键上
 * （TextSegment 按文本+元数据值相等，langchain4j 1.22 字节码验证过——见审查报告排除项 20）。
 */
class RrfFuseTest {

    private static TextSegment seg(String text) {
        return TextSegment.from(text);
    }

    @Test
    void bothEmptyYieldsEmpty() {
        assertTrue(RagTasks.rrfFuse(List.of(), List.of(), 3).isEmpty());
    }

    @Test
    void topKGreaterThanTotalReturnsAll() {
        List<TextSegment> out = RagTasks.rrfFuse(List.of(seg("a"), seg("b")), List.of(seg("c")), 5);
        assertEquals(3, out.size());
    }

    @Test
    void zeroTopKReturnsEmpty() {
        assertTrue(RagTasks.rrfFuse(List.of(seg("a")), List.of(seg("b")), 0).isEmpty());
    }

    @Test
    void sameContentDifferentInstancesFuseIntoOne() {
        // 向量路返回 store 里的实例，BM25 路是重切出来的另一批实例——值相等必须合并计分
        TextSegment viaVector = seg("报销上限三百元");
        TextSegment viaBm25 = seg("报销上限三百元");
        List<TextSegment> out = RagTasks.rrfFuse(List.of(viaVector, seg("其他")), List.of(viaBm25), 3);
        assertEquals(2, out.size(), "同内容两实例应融合为一个候选");
        assertEquals("报销上限三百元", out.get(0).text(), "两路都命中的候选分数叠加应排第一");
    }

    @Test
    void fusedBeatsSingleTopRank() {
        // x 在两路各排第 2：1/62+1/62；y 只在一路排第 1：1/61。x 总分更高应排前
        List<TextSegment> out = RagTasks.rrfFuse(
                List.of(seg("z"), seg("x")),
                List.of(seg("w"), seg("x")),
                2);
        assertEquals("x", out.get(0).text());
    }
}
