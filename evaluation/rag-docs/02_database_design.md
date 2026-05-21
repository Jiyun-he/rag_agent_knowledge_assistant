# 数据库表结构说明

本项目的数据库设计围绕知识库、问答历史和评测三组业务展开。知识库相关表包括 kb_space、kb_document、kb_chunk 和 kb_embedding_task。问答历史相关表包括 qa_session、qa_message 和 qa_reference。评测相关表包括 eval_dataset、eval_case、eval_run 和 eval_case_result。

kb_space 表表示知识库空间，用于隔离不同业务场景或不同文档集合。kb_document 表保存原始文档内容和文档级元数据，包括 spaceId、title、content、sourceType、sourceUri、status 和 chunkCount。kb_chunk 表保存文档切分后的文本片段，是检索和向量化的基本单位，核心字段包括 documentId、spaceId、chunkIndex、content、charCount、embeddingStatus、vectorId 和 status。embeddingStatus 用于表示 chunk 的向量化状态，0 表示 pending，1 表示 done，2 表示 failed。

kb_embedding_task 表记录文档异步向量化任务，包括 documentId、spaceId、taskType、status、totalChunkCount、pendingChunkCount、successCount、failedCount、skippedCount、errorMessage、startedAt 和 finishedAt。任务状态中，0 表示 pending，1 表示 running，2 表示 success，3 表示 failed，4 表示 partial_success。

qa_session 表保存问答会话，qa_message 表保存用户消息和 AI 回答，qa_reference 表保存回答引用的 chunk 信息。qa_reference 中的 messageId 指向 assistant 消息，chunkId、documentId、spaceId 和 chunkIndex 用于追踪回答依据。

eval_dataset 表表示评测数据集，eval_case 表表示评测问题，eval_run 表表示一次评测运行，eval_case_result 表保存单个评测问题在某次 run 下的检索和回答结果。EvalRun 记录整体指标，例如 avgRecallAtK、hitRateAtK、mrr、avgAnswerKeywordHit、citationCorrectRate 和 avgLatencyMs。
