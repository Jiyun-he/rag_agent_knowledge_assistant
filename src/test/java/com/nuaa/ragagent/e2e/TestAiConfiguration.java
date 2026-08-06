package com.nuaa.ragagent.e2e;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 测试专用 AI bean：以 fake 实现覆盖自动配置的 OpenAI Chat/Embedding。
 *
 * <p>主配置已通过 spring.ai.model.chat=mock / embedding=mock 关闭 OpenAI 自动配置，
 * 此处 @Primary 再加一道保险，避免任何自动配置兜底创建真实 bean。</p>
 */
@TestConfiguration
public class TestAiConfiguration {

    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        return new FakeEmbeddingModel();
    }

    @Bean
    @Primary
    public ChatModel chatModel() {
        return new FakeChatModel();
    }
}
