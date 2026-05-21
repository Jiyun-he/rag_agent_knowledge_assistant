# 项目总体说明

本项目名为 rag_agent_knowledge_assistant，主包路径为 com.nuaa.ragagent，是一个基于 Spring Boot、MySQL、Qdrant 和 OpenAI-compatible 大模型接口实现的 RAG 知识库问答系统后端。系统的核心目标是将文档资料转化为可检索的知识库，并在用户提问时通过检索增强生成回答。

项目的基本链路包括知识库空间创建、文档创建、文本切分、chunk 存储、异步向量化、Qdrant 向量写入、RAG 检索、RAG 问答、问答历史保存、引用追踪和 RAG Evaluation 评测。MySQL 主要负责保存结构化业务数据，例如知识库空间、文档、chunk、向量化任务、问答历史和评测记录。Qdrant 主要负责保存 chunk embedding 向量，并提供向量相似度检索能力。

在一次完整 RAG 问答中，用户提交问题后，系统首先根据 spaceId 限定知识库范围，然后通过 RagRetrievalService 执行检索。检索结果会被转换为上下文，由 RagPromptBuilder 构建 prompt，再通过 ChatClient 调用大模型生成回答。回答生成后，系统会保存 QaSession、QaMessage 和 QaReference，从而保留问答历史和引用来源。

项目当前支持 VECTOR_ONLY、KEYWORD_ONLY、HYBRID 和 HYBRID_RERANK 四种检索模式。其中 VECTOR_ONLY 侧重语义相似度，KEYWORD_ONLY 侧重精确关键词，HYBRID 同时结合向量召回和关键词召回，HYBRID_RERANK 在混合召回后进一步根据精确术语和技术符号进行规则重排序。
