package com.nuaa.ragagent.util;

import com.nuaa.ragagent.response.SearchChunkResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RagPromptBuilder {

    private static final int MAX_CONTEXT_CHUNK_LENGTH = 2000;

    public String buildPrompt(String question, List<SearchChunkResponse> chunks) {
        StringBuilder builder = new StringBuilder();

        builder.append("You are an enterprise RAG knowledge assistant.\n");
        builder.append("Your task is to answer the user's question based only on the provided context.\n");
        builder.append("If the context is insufficient, say that the current knowledge base does not contain enough information.\n");
        builder.append("Do not fabricate facts that are not supported by the context.\n");
        builder.append("When possible, mention which references support your answer, such as [Reference 1], [Reference 2].\n\n");

        builder.append("User Question:\n");
        builder.append(question).append("\n\n");

        builder.append("Retrieved Context:\n");

        for (int i = 0; i < chunks.size(); i++) {
            SearchChunkResponse chunk = chunks.get(i);

            builder.append("\n[Reference ").append(i + 1).append("]\n");

            if (chunk.getDocumentId() != null) {
                builder.append("Document ID: ").append(chunk.getDocumentId()).append("\n");
            }

            if (chunk.getChunkId() != null) {
                builder.append("Chunk ID: ").append(chunk.getChunkId()).append("\n");
            }

            if (chunk.getChunkIndex() != null) {
                builder.append("Chunk Index: ").append(chunk.getChunkIndex()).append("\n");
            }

            builder.append("Content:\n");
            builder.append(limitText(chunk.getContent(), MAX_CONTEXT_CHUNK_LENGTH)).append("\n");
        }

        builder.append("\nAnswer Requirements:\n");
        builder.append("1. Answer in Chinese unless the user explicitly asks for another language.\n");
        builder.append("2. Keep the answer accurate, structured, and based on the retrieved context.\n");
        builder.append("3. Do not mention internal implementation details such as embeddings, vector databases, or prompt construction unless the user asks.\n");
        builder.append("4. If the answer is based on a specific reference, mark it with [Reference N].\n");

        return builder.toString();
    }

    private String limitText(String text, int maxLength) {
        if (text == null) {
            return "";
        }

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength) + "...";
    }
}