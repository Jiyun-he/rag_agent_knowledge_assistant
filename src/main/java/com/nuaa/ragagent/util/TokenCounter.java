package com.nuaa.ragagent.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 jtokkit（tiktoken 的 Java 移植）的 token 计数，使用 cl100k_base，
 * 与 text-embedding-3-small 的切词一致。
 *
 * @author jiyunhe
 */
@Component
public class TokenCounter {

    private final Encoding encoding = Encodings.newDefaultEncodingRegistry()
            .getEncoding(EncodingType.CL100K_BASE);

    public int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return encoding.countTokens(text);
    }

    /** 按 chunkSize 切分、相邻 chunk 重叠 overlap token，用于单个超大结构单元的兜底切分。 */
    public List<String> splitByTokens(String text, int chunkSize, int overlap) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return result;
        }
        if (chunkSize <= 0) {
            result.add(text);
            return result;
        }
        overlap = Math.max(0, Math.min(overlap, chunkSize / 2));

        IntArrayList tokens = encoding.encode(text);
        int n = tokens.size();
        int start = 0;
        while (start < n) {
            int end = Math.min(start + chunkSize, n);
            IntArrayList sub = new IntArrayList(end - start);
            for (int j = start; j < end; j++) {
                sub.add(tokens.get(j));
            }
            result.add(encoding.decode(sub));
            if (end >= n) {
                break;
            }
            start = end - overlap;
            if (start < 0) {
                start = 0;
            }
        }
        return result;
    }
}
