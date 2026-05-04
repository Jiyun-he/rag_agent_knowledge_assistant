package com.nuaa.ragagent.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunker {

    private final int maxSize;

    private final int overlapSize;

    public TextChunker(
            @Value("${rag.chunk.max-size:500}") int maxSize,
            @Value("${rag.chunk.overlap-size:80}") int overlapSize
    ) {
        this.maxSize = Math.max(100, maxSize);
        this.overlapSize = Math.max(0, Math.min(overlapSize, this.maxSize / 2));
    }

    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        String normalizedText = text
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();

        int length = normalizedText.length();
        int start = 0;

        while (start < length) {
            int end = Math.min(start + maxSize, length);

            String chunk = normalizedText.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            if (end >= length) {
                break;
            }

            start = end - overlapSize;
            if (start < 0) {
                start = 0;
            }
        }

        return chunks;
    }
}