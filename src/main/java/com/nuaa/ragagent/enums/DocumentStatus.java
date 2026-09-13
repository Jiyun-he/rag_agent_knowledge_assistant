package com.nuaa.ragagent.enums;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * 文档生命周期状态。
 * <p>数值映射兼容历史数据：ACTIVE=1（原 active）、INVALID=0（原 deleted）无需迁移，
 * INDEXING=2、FAILED=3 为新增状态。</p>
 *
 * <ul>
 *     <li>INDEXING：新文档创建后的初始状态，向量化进行中，未生效（不可检索）</li>
 *     <li>ACTIVE：全部 chunk 向量化成功，生效（可检索）</li>
 *     <li>INVALID：被删除或更新后失效的旧文档，永不物理删除</li>
 *     <li>FAILED：向量化存在失败 chunk，尚未生效，可重试</li>
 * </ul>
 *
 * @author jiyunhe
 */
public enum DocumentStatus {

    /** 失效（历史值 0） */
    INVALID(0),

    /** 生效（历史值 1） */
    ACTIVE(1),

    /** 向量化中（新增） */
    INDEXING(2),

    /** 向量化失败（新增） */
    FAILED(3);

    private final int value;

    DocumentStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    /**
     * 按数值解析状态。
     *
     * @param value 状态数值，空或未知时返回 {@link #INVALID}
     * @return 对应枚举
     */
    public static DocumentStatus of(Integer value) {
        if (value == null) {
            return INVALID;
        }

        return Arrays.stream(values())
                .filter(s -> Objects.equals(s.value, value))
                .findFirst()
                .orElse(INVALID);
    }

    /**
     * 按数值解析状态（无默认值语义，供需要区分空值的场景使用）。
     */
    public static Optional<DocumentStatus> parse(Integer value) {
        if (value == null) {
            return Optional.empty();
        }

        return Arrays.stream(values())
                .filter(s -> Objects.equals(s.value, value))
                .findFirst();
    }
}
