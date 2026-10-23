package com.gateflow.workers;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 边界值测试：中文二元组分词（bigrams）——RagTasks 的字面匹配基石。
 * 纯逻辑无外部依赖，CI 可跑。
 */
class BigramsTest {

    @Test
    void emptyStringYieldsNothing() {
        assertEquals(List.of(), RagTasks.bigrams(""));
    }

    @Test
    void singleCharIsItselfATerm() {
        assertEquals(List.of("a"), RagTasks.bigrams("a"));
        assertEquals(List.of("你"), RagTasks.bigrams("你"));
    }

    @Test
    void twoCharsFormOneBigram() {
        assertEquals(List.of("你好"), RagTasks.bigrams("你好"));
    }

    @Test
    void latinRunIsOneTokenThenCrossesIntoCjk() {
        // "abc你好"：英文连续段整体一个词，再与中文逐字组成二元
        assertEquals(List.of("abc你", "你好"), RagTasks.bigrams("abc你好"));
    }

    @Test
    void whitespaceSplitsButIsNotATerm() {
        assertEquals(List.of("ab"), RagTasks.bigrams("a b"));
        assertEquals(List.of(), RagTasks.bigrams("  "));
    }

    @Test
    void punctuationParticipatesAsAChar() {
        // 现状语义：全角标点非空白，作为字符参与二元（查询与文档同规则，对称）
        assertEquals(List.of("报。", "。销"), RagTasks.bigrams("报。销"));
    }

    @Test
    void digitsGroupWithLatin() {
        assertEquals(List.of("2026年", "年预", "预算"), RagTasks.bigrams("2026年预算"));
    }

    @Test
    void leadingAndTrailingPunctuation() {
        assertEquals(List.of("。报", "报销"), RagTasks.bigrams("。报销"));
    }
}
