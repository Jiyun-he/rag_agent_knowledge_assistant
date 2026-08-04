package com.nuaa.ragagent.enums;

/**
 * 检索模式。
 * <p>定义知识库检索时所采用的检索策略，支持纯向量、纯关键词、混合以及混合加重排四种模式。</p>
 *
 * @author jiyunhe
 */
public enum RetrievalMode {

    /**
     * 纯向量检索：仅基于向量相似度进行检索。
     */
    VECTOR_ONLY,

    /**
     * 纯关键词检索：仅基于关键词进行检索。
     */
    KEYWORD_ONLY,

    /**
     * 混合检索：融合向量检索与关键词检索的结果。
     */
    HYBRID,

    /**
     * 混合检索加重排：在混合检索的基础上对结果进行重排。
     */
    HYBRID_RERANK;

    /**
     * 将字符串转换为对应的检索模式。
     * <p>字符串为空、空白或不合法时，默认返回 {@link #VECTOR_ONLY}。</p>
     *
     * @param value 检索模式字符串，如 "hybrid"
     * @return 对应的 {@link RetrievalMode}
     */
    public static RetrievalMode from(String value) {
        if (value == null || value.trim().isEmpty()) {
            return VECTOR_ONLY;
        }

        try {
            return RetrievalMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return VECTOR_ONLY;
        }
    }
}