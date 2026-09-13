package com.nuaa.ragagent.enums;

import java.util.Arrays;

/**
 * 持久化索引任务状态机。
 *
 * <ul>
 *     <li>PENDING：已登记待执行（含超时回收后重新排队）</li>
 *     <li>RUNNING：已被 worker 抢占，执行中</li>
 *     <li>RETRY_WAIT：执行失败，等待 {@code next_retry_at} 后重试</li>
 *     <li>SUCCESS：执行成功（终态）</li>
 *     <li>FAILED：重试耗尽后失败（终态），可人工重试</li>
 * </ul>
 *
 * @author jiyunhe
 */
public enum TaskStatus {

    /** 待执行 */
    PENDING(0),

    /** 执行中 */
    RUNNING(1),

    /** 等待重试 */
    RETRY_WAIT(2),

    /** 成功（终态） */
    SUCCESS(3),

    /** 失败（终态，可人工重试） */
    FAILED(4);

    private final int value;

    TaskStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    /** 是否为活跃状态（占用去重名额，同一 Document+Type 不得重复登记） */
    public boolean isActive() {
        return this == PENDING || this == RUNNING || this == RETRY_WAIT;
    }

    /** 是否为终态 */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
    }

    /**
     * 按数值解析状态，未知或空值回退 {@link #FAILED}。
     */
    public static TaskStatus of(Integer value) {
        if (value == null) {
            return FAILED;
        }
        return Arrays.stream(values())
                .filter(s -> s.value == value)
                .findFirst()
                .orElse(FAILED);
    }
}
