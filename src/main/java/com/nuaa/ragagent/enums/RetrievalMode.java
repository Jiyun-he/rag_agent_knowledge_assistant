package com.nuaa.ragagent.enums;

public enum RetrievalMode {

    VECTOR_ONLY,

    KEYWORD_ONLY,

    HYBRID,

    HYBRID_RERANK;

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