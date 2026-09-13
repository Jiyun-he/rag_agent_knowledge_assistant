package com.nuaa.ragagent.exception;
/**
 * 索引构建中止：构建期间文档被删除或更新（置为 INVALID），本次构建结果作废。
 *
 * <p>与 {@link BusinessException} 的区别在于处理方式：本异常表示“无需重试的确定失败”，
 * 任务应直接置 FAILED 终态，而不是按指数退避反复重试——对已失效文档重试没有意义，
 * 且构建入口本身也会拒绝 INVALID 文档。</p>
 *
 * @author jiyunhe
 */
public class IndexBuildAbortedException extends RuntimeException {

    public IndexBuildAbortedException(String message) {
        super(message);
    }
}
