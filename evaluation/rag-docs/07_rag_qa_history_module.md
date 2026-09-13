# RAG 问答与引用追踪模块说明

RAG 问答模块负责把检索结果转化为大模型可用的上下文，并生成最终回答。核心类包括 RagQaController、RagQaService、RagQaServiceImpl、RagPromptBuilder、AskRequest、AskResponse 和 RagAnswerReferenceResponse。对外接口为 POST /api/rag/ask。

AskRequest 包含 spaceId、sessionId、question、topK、retrievalMode 和 candidateK。RagQaServiceImpl 接收请求后，会先校验 spaceId 和 question，再构造 SearchChunksRequest，并调用 RagRetrievalService.search 执行检索。如果没有检索到有效 chunk，系统会返回“当前知识库中没有检索到足够相关的内容，因此无法基于已有知识库给出可靠回答”。

如果检索到相关 chunk，RagPromptBuilder 会根据 question 和检索结果构造 prompt。prompt 会要求模型尽量基于提供的 context 回答，不编造 context 中没有的信息，并把检索结果按顺序编号为 [Reference 1] 到 [Reference N]，要求模型在回答中用 [Reference N] 标注引用来源。随后系统通过 ChatClient 调用大模型生成回答。

问答历史与引用追踪由 QaHistoryServiceImpl 负责。一次 ask 会保存用户问题和 AI 回答。QaSession 表示一次会话，QaMessage 表示会话中的 user 或 assistant 消息，QaReference 表示 assistant 回答引用的 chunk。AskResponse 中会返回 sessionId、userMessageId、assistantMessageId 和 references，方便后续查询完整会话和回答依据。
