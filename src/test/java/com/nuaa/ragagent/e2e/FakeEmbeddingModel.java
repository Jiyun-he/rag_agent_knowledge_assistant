package com.nuaa.ragagent.e2e;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 确定性的内存 EmbeddingModel，用于端到端测试替代真实 OpenAI Embedding 调用。
 *
 * <p>采用 hashing trick：对文本做字符 2-gram，每个 gram 经固定种子映射到 128 维向量空间的 4 个
 * 随机分量，求和后 L2 归一化。两个共享相同 2-gram 的文本会得到高余弦相似度，使 VECTOR_ONLY
 * 检索与评测的 Recall@K / Hit@K 具备确定性语义，可被测试断言。</p>
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSIONS = 128;

    /** 每个 2-gram 在向量空间中的非零分量个数 */
    private static final int NON_ZERO_SLOTS = 4;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> instructions = request.getInstructions();
        List<Embedding> embeddings = new ArrayList<>(instructions.size());
        for (int i = 0; i < instructions.size(); i++) {
            embeddings.add(new Embedding(embed(instructions.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text == null || text.isEmpty()) {
            return vector;
        }

        String normalized = text.toLowerCase();
        for (int i = 0; i < normalized.length() - 1; i++) {
            addGram(vector, normalized.substring(i, i + 2));
        }
        if (normalized.length() == 1) {
            addGram(vector, normalized);
        }

        normalize(vector);
        return vector;
    }

    private void addGram(float[] vector, String gram) {
        Random random = new Random(gram.hashCode());
        for (int i = 0; i < NON_ZERO_SLOTS; i++) {
            int dim = random.nextInt(DIMENSIONS);
            float value = random.nextFloat() * 2 - 1;
            vector[dim] += value;
        }
    }

    private void normalize(float[] vector) {
        double sum = 0.0;
        for (float v : vector) {
            sum += v * v;
        }
        if (sum <= 0) {
            return;
        }
        double norm = Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
    }
}
