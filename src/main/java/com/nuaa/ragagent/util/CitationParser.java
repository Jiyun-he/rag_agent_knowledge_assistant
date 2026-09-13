package com.nuaa.ragagent.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 从模型回答中解析 {@code [Reference N]} 引用标记。
 *
 * <p>与 {@link RagPromptBuilder} 的编号约定配套：prompt 中的上下文按
 * {@code [Reference 1] ... [Reference N]} 顺序编号，因此编号 N 对应本次检索结果列表的第 N 项
 * （下标 N-1）。评测据此把回答里的引用映射回 chunkId，从而度量“回答是否引用了正确的来源”。</p>
 *
 * <p>解析规则：</p>
 * <ul>
 *     <li>大小写不敏感，允许编号前后有空白与可选的 {@code #}</li>
 *     <li>同时接受半角 {@code []} 与全角 {@code 【】}（中文回答常见）</li>
 *     <li>重复引用去重并保持首次出现顺序，避免同一来源反复出现抬高/拉低分母</li>
 *     <li>编号越界（&lt; 1 或 &gt; 上下文条数）不计入有效引用，单独计数——这是“编造引用”的信号</li>
 * </ul>
 *
 * <p>刻意不解析裸编号 {@code [1]}：它与 Markdown 链接/脚注语法歧义过大，会引入误判。</p>
 *
 * @author jiyunhe
 */
@Component
public class CitationParser {

    private static final Pattern REFERENCE_PATTERN = Pattern.compile(
            "[\\[【]\\s*reference\\s*#?\\s*(\\d+)\\s*[\\]】]",
            Pattern.CASE_INSENSITIVE);

    /**
     * 引用解析结果。
     *
     * @param inRangeIndices 落在上下文范围内的引用编号（去重保序，1-based）
     * @param outOfRangeCount 越界引用编号的个数（去重后）
     */
    public record CitationParseResult(List<Integer> inRangeIndices, int outOfRangeCount) {

        /** 是否解析到任何引用标记（含越界） */
        public boolean hasAnyCitation() {
            return !inRangeIndices.isEmpty() || outOfRangeCount > 0;
        }
    }

    /**
     * 解析回答中的引用标记。
     *
     * @param answer      模型回答，可为 null/空白（返回空结果）
     * @param contextSize 本次检索上下文条数，用于判定编号是否越界
     * @return 解析结果；answer 为空白时返回空结果（调用方需区分“没回答”与“回答了但没引用”）
     */
    public CitationParseResult parse(String answer, int contextSize) {
        if (!StringUtils.hasText(answer)) {
            return new CitationParseResult(List.of(), 0);
        }

        LinkedHashSet<Integer> inRange = new LinkedHashSet<>();
        LinkedHashSet<Integer> outOfRange = new LinkedHashSet<>();

        Matcher matcher = REFERENCE_PATTERN.matcher(answer);
        while (matcher.find()) {
            int index;
            try {
                index = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                // 编号超出 int 范围，按越界处理
                continue;
            }

            if (index >= 1 && index <= contextSize) {
                inRange.add(index);
            } else {
                outOfRange.add(index);
            }
        }

        return new CitationParseResult(new ArrayList<>(inRange), outOfRange.size());
    }
}
