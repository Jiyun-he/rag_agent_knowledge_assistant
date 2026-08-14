package com.nuaa.ragagent.util;

import java.util.List;

/**
 * 文档切分策略统一入口。按 {@code rag.chunk.strategy} 配置选择实现：
 * FIXED_SIZE（基线，不变）或 STRUCTURE_AWARE。
 *
 * @author jiyunhe
 */
public interface Chunker {

    /**
     * 将整篇 Markdown 文本按当前切分策略切分为若干 chunk。
     *
     * @param text 待切分的全文 Markdown
     * @return 切分后的 chunk 列表；text 为空或空白时返回空列表
     */
    List<String> split(String text);
}
