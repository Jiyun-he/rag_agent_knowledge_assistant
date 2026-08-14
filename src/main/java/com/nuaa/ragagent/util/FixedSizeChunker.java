package com.nuaa.ragagent.util;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 定长 overlap 切分策略（基线）。完全委托现有 {@link TextChunker}，行为不变。
 *
 * @author jiyunhe
 */
@Component
@ConditionalOnProperty(name = "rag.chunk.strategy", havingValue = "FIXED_SIZE", matchIfMissing = true)
public class FixedSizeChunker implements Chunker {

    private final TextChunker textChunker;

    public FixedSizeChunker(TextChunker textChunker) {
        this.textChunker = textChunker;
    }

    @Override
    public List<String> split(String text) {
        return textChunker.split(text);
    }
}
