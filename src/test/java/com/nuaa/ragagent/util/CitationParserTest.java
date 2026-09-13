package com.nuaa.ragagent.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CitationParser} 的解析规则测试：编号范围、去重、大小写、全角括号与越界引用。
 */
class CitationParserTest {

    private final CitationParser parser = new CitationParser();

    @Test
    void parse_nullOrBlankAnswer_returnsEmpty() {
        assertThat(parser.parse(null, 3).inRangeIndices()).isEmpty();
        assertThat(parser.parse("   ", 3).inRangeIndices()).isEmpty();
        assertThat(parser.parse(null, 3).hasAnyCitation()).isFalse();
    }

    @Test
    void parse_singleReference_returnsIndex() {
        CitationParser.CitationParseResult result = parser.parse("答案是 A。[Reference 2]", 3);

        assertThat(result.inRangeIndices()).containsExactly(2);
        assertThat(result.outOfRangeCount()).isZero();
    }

    @Test
    void parse_multipleReferences_deduplicatesPreservingOrder() {
        CitationParser.CitationParseResult result =
                parser.parse("[Reference 3] 先说3，又说1，再重复 [Reference 3] 与 [Reference 1]", 3);

        assertThat(result.inRangeIndices()).containsExactly(3, 1);
    }

    @Test
    void parse_outOfRangeReference_countedSeparatelyNotAsValid() {
        // 上下文只有 3 条，引用第 9 条属于编造引用
        CitationParser.CitationParseResult result =
                parser.parse("依据 [Reference 9] 与 [Reference 1]", 3);

        assertThat(result.inRangeIndices()).containsExactly(1);
        assertThat(result.outOfRangeCount()).isEqualTo(1);
        assertThat(result.hasAnyCitation()).isTrue();
    }

    @Test
    void parse_zeroIndex_isOutOfRange() {
        CitationParser.CitationParseResult result = parser.parse("[Reference 0]", 3);

        assertThat(result.inRangeIndices()).isEmpty();
        assertThat(result.outOfRangeCount()).isEqualTo(1);
    }

    @Test
    void parse_negativeIndex_isNotMatchedAsCitationAtAll() {
        // 正则只接受 \d+，负号不参与匹配，因此负数编号不算“越界引用”而是完全不被识别
        CitationParser.CitationParseResult result = parser.parse("[Reference -1]", 3);

        assertThat(result.inRangeIndices()).isEmpty();
        assertThat(result.outOfRangeCount()).isZero();
        assertThat(result.hasAnyCitation()).isFalse();
    }

    @Test
    void parse_caseInsensitiveAndToleratesWhitespaceAndHash() {
        assertThat(parser.parse("[reference 2]", 3).inRangeIndices()).containsExactly(2);
        assertThat(parser.parse("[REFERENCE 2]", 3).inRangeIndices()).containsExactly(2);
        assertThat(parser.parse("[Reference   #  2 ]", 3).inRangeIndices()).containsExactly(2);
    }

    @Test
    void parse_fullWidthBrackets_areAccepted() {
        assertThat(parser.parse("根据【Reference 1】可知", 3).inRangeIndices()).containsExactly(1);
    }

    @Test
    void parse_bareNumericBracket_isNotTreatedAsCitation() {
        // 裸编号 [1] 与 Markdown 链接/脚注歧义过大，刻意不解析
        CitationParser.CitationParseResult result = parser.parse("见 [1] 与 [2]", 3);

        assertThat(result.inRangeIndices()).isEmpty();
        assertThat(result.outOfRangeCount()).isZero();
        assertThat(result.hasAnyCitation()).isFalse();
    }

    @Test
    void parse_zeroContextSize_anyCitationIsOutOfRange() {
        CitationParser.CitationParseResult result = parser.parse("[Reference 1]", 0);

        assertThat(result.inRangeIndices()).isEmpty();
        assertThat(result.outOfRangeCount()).isEqualTo(1);
    }
}
