package com.nuaa.ragagent.response;
/**
 * 索引对账结果。
 *
 * @author jiyunhe
 */
public class ReconcileResult {

    /** Qdrant 扫描的 point 数量 */
    private int qdrantScanned;

    /** Qdrant 清理的孤儿 point 数量 */
    private int qdrantOrphanCount;

    /** ES 扫描的索引文档数量 */
    private int esScanned;

    /** ES 清理的孤儿索引文档数量 */
    private int esOrphanCount;

    /** 缺失索引的 chunk 数量（ACTIVE 文档期望存在但 Qdrant/ES 缺失） */
    private int missingChunkCount;

    /** 存在缺失 chunk、需要补建索引的文档数量 */
    private int repairDocumentCount;

    /** 实际登记的 REPAIR_INDEX 任务数（可能因去重少于 repairDocumentCount） */
    private int repairTaskInsertedCount;

    /** 对账耗时（毫秒） */
    private long durationMs;

    public int getQdrantScanned() {
        return qdrantScanned;
    }

    public ReconcileResult setQdrantScanned(int qdrantScanned) {
        this.qdrantScanned = qdrantScanned;
        return this;
    }

    public int getQdrantOrphanCount() {
        return qdrantOrphanCount;
    }

    public ReconcileResult setQdrantOrphanCount(int qdrantOrphanCount) {
        this.qdrantOrphanCount = qdrantOrphanCount;
        return this;
    }

    public int getEsScanned() {
        return esScanned;
    }

    public ReconcileResult setEsScanned(int esScanned) {
        this.esScanned = esScanned;
        return this;
    }

    public int getEsOrphanCount() {
        return esOrphanCount;
    }

    public ReconcileResult setEsOrphanCount(int esOrphanCount) {
        this.esOrphanCount = esOrphanCount;
        return this;
    }

    public int getMissingChunkCount() {
        return missingChunkCount;
    }

    public ReconcileResult setMissingChunkCount(int missingChunkCount) {
        this.missingChunkCount = missingChunkCount;
        return this;
    }

    public int getRepairDocumentCount() {
        return repairDocumentCount;
    }

    public ReconcileResult setRepairDocumentCount(int repairDocumentCount) {
        this.repairDocumentCount = repairDocumentCount;
        return this;
    }

    public int getRepairTaskInsertedCount() {
        return repairTaskInsertedCount;
    }

    public ReconcileResult setRepairTaskInsertedCount(int repairTaskInsertedCount) {
        this.repairTaskInsertedCount = repairTaskInsertedCount;
        return this;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public ReconcileResult setDurationMs(long durationMs) {
        this.durationMs = durationMs;
        return this;
    }
}
