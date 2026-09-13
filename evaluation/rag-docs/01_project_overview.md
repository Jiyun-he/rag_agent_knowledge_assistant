# 项目总体说明

本项目名为 rag_agent_knowledge_assistant，主包路径为 com.nuaa.ragagent，是一个基于 Spring Boot、MySQL、Qdrant 和 OpenAI-compatible 大模型接口实现的 RAG 知识库问答系统后端。系统的核心目标是将文档资料转化为可检索的知识库，并在用户提问时通过检索增强生成回答。

项目的基本链路包括知识库空间创建、文档创建、文本切分、chunk 存储、索引任务登记、异步构建 Qdrant 向量索引与 Elasticsearch 关键词索引、RAG 检索、RAG 问答、问答历史保存、引用追踪和 RAG Evaluation 评测。MySQL 主要负责保存结构化业务数据，例如知识库空间、文档、chunk、索引任务、问答历史和评测记录。Qdrant 主要负责保存 chunk embedding 向量，并提供向量相似度检索能力。Elasticsearch 配合 smartcn 中文分词提供关键词检索能力。

文档具有 INVALID、ACTIVE、INDEXING 和 FAILED 四种状态。新建文档初始为 INDEXING，尚未生效；文档下全部 chunk 索引成功后才转为 ACTIVE，才参与检索；只要存在失败的 chunk，文档即为 FAILED，可以重试；被更新或删除的旧文档转为 INVALID，但永不物理删除。检索只返回 ACTIVE 文档，出口另有以 MySQL 为准的有效性校验。

索引工作由 kb_index_task 持久化任务承载。文档创建、更新、删除在事务提交后登记索引任务，由 worker 异步执行；任务类型包括 BUILD_INDEX、DELETE_INDEX 和 REPAIR_INDEX，其中更新等价于对旧文档登记 DELETE_INDEX、对新文档登记 BUILD_INDEX。系统还会周期性执行索引对账，清理 Qdrant 和 Elasticsearch 中的孤儿索引，并为索引缺失的 ACTIVE 文档登记 REPAIR_INDEX。

在一次完整 RAG 问答中，用户提交问题后，系统首先根据 spaceId 限定知识库范围，然后通过 RagRetrievalService 执行检索。检索结果会被转换为上下文，由 RagPromptBuilder 构建 prompt，再通过 ChatClient 调用大模型生成回答。回答生成后，系统会保存 QaSession、QaMessage 和 QaReference，从而保留问答历史和引用来源。

项目当前支持 VECTOR_ONLY、KEYWORD_ONLY、HYBRID 和 HYBRID_RERANK 四种检索模式。其中 VECTOR_ONLY 侧重语义相似度，KEYWORD_ONLY 基于 Elasticsearch 的 smartcn 中文分词做关键词召回，HYBRID 通过等权 RRF 融合向量召回和关键词召回，HYBRID_RERANK 在混合召回后进一步调用 TEI rerank 服务进行重排序。
