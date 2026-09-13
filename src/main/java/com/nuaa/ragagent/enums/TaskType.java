package com.nuaa.ragagent.enums;

import java.util.Arrays;

/**
 * 持久化索引任务类型。
 *
 * <ul>
 *     <li>BUILD_INDEX：为新文档建立 Qdrant 向量 + ES 关键词索引（文档创建/更新后自动登记）</li>
 *     <li>DELETE_INDEX：删除文档在 Qdrant/ES 中的索引（文档删除/更新旧文档后自动登记）</li>
 *     <li>REPAIR_INDEX：对账发现索引缺失后的强制补建（全量重写文档索引）</li>
 * </ul>
 *
 * @author jiyunhe
 */
public enum TaskType {

    BUILD_INDEX,

    DELETE_INDEX,

    REPAIR_INDEX;

    /**
     * 按名称解析任务类型，未知返回 null。
     */
    public static TaskType of(String value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(value))
                .findFirst()
                .orElse(null);
    }

    /**
     * 去重分组：同一文档下，同组内不允许存在多个活跃任务。
     *
     * <p>BUILD_INDEX 与 REPAIR_INDEX 都是“写索引”任务，共用 BUILD 组——两者并发执行会互相
     * 覆盖写入、且一方中止时的清理会误删另一方刚写入的索引；DELETE_INDEX 是清理操作，独立成组。</p>
     *
     * <p>该映射必须与 {@code kb_index_task.dedup_group} 生成列表达式保持一致：
     * 这里决定应用层查询口径，生成列 + 唯一索引 {@code uk_task_active_dedup} 负责并发兜底。</p>
     */
    public String dedupGroup() {
        return switch (this) {
            case BUILD_INDEX, REPAIR_INDEX -> "BUILD";
            case DELETE_INDEX -> "DELETE";
        };
    }
}
