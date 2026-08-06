package com.nuaa.ragagent.e2e;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

/**
 * 固定回答的 ChatModel，用于端到端测试替代真实 LLM 调用。
 *
 * <p>评测 case 的 expectedKeywords 设计为本常量回答的子集，使 Answer Keyword Hit 指标
 * 稳定命中，用于验证评测指标的聚合计算是否正确（而非验证 LLM 生成质量）。</p>
 */
public class FakeChatModel implements ChatModel {

    /** 覆盖所有评测 case 期望关键词的固定回答 */
    public static final String FAKE_ANSWER =
            "根据知识库检索结果，异步向量化任务用于管理文档向量化，"
                    + "关键词检索基于 Elasticsearch 的 smartcn 中文分词实现，"
                    + "RAG 评测的指标包括 Recall、Hit、MRR，并支持重排。";

    @Override
    public ChatResponse call(Prompt prompt) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(FAKE_ANSWER))));
    }
}
