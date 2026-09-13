# 数据库表结构说明

本项目的数据库设计围绕知识库、问答历史和评测三组业务展开。知识库相关表包括 kb_space、kb_document、kb_chunk 和 kb_index_task。问答历史相关表包括 qa_session、qa_message 和 qa_reference。评测相关表包括 eval_dataset、eval_case、eval_run 和 eval_case_result。

kb_space 表表示知识库空间，用于隔离不同业务场景或不同文档集合。kb_document 表保存原始文档内容和文档级元数据，包括 spaceId、title、content、sourceType、sourceUri、status 和 chunkCount。kb_document.status 表示文档生命周期状态，共有四态：0 表示 INVALID，即被删除或更新后失效的旧文档，永不物理删除；1 表示 ACTIVE，即全部 chunk 索引成功、生效可检索；2 表示 INDEXING，即索引构建中、尚未生效；3 表示 FAILED，即存在失败 chunk、可以重试、尚未生效。检索只返回 ACTIVE 文档，出口另有以 MySQL 为准的有效性校验。

kb_chunk 表保存文档切分后的文本片段，是检索和索引构建的基本单位，核心字段包括 documentId、spaceId、chunkIndex、content、charCount、embeddingStatus、vectorId 和 status。embeddingStatus 用于表示 chunk 的向量化状态，0 表示 pending，1 表示 done，2 表示 failed。

kb_index_task 表记录持久化索引任务，包括 documentId、spaceId、taskType、status、retryCount、nextRetryAt、totalChunkCount、pendingChunkCount、successCount、failedCount、skippedCount、errorMessage、startedAt 和 finishedAt。taskType 的取值为 BUILD_INDEX（新文档建索引）、DELETE_INDEX（删文档索引）和 REPAIR_INDEX（对账补建），文档更新等价于 DELETE_INDEX(旧) 加 BUILD_INDEX(新)。任务状态中，0 表示 pending，1 表示 running，2 表示 retry_wait，3 表示 success，4 表示 failed。任务去重由生成列加唯一索引在数据库层保证：BUILD_INDEX 与 REPAIR_INDEX 共用 BUILD 分组，使同一文档的写索引任务互斥；DELETE_INDEX 独立成组。

qa_session 表保存问答会话，qa_message 表保存用户消息和 AI 回答，qa_reference 表保存回答引用的 chunk 信息。qa_reference 中的 messageId 指向 assistant 消息，chunkId、documentId、spaceId 和 chunkIndex 用于追踪回答依据。

eval_dataset 表表示评测数据集，eval_case 表表示评测问题，eval_run 表表示一次评测运行，eval_case_result 表保存单个评测问题在某次 run 下的检索和回答结果。

EvalRun 记录检索与回答的整体指标，例如 avgRecallAtK、hitRateAtK、mrr、avgAnswerKeywordHit、avgCitationPrecision、avgCitationRecall、groundedRate 和 avgLatencyMs。引用正确率不单独统计，它与 Hit@K 完全等价，不构成独立指标。EvalCaseResult 除检索指标外，还记录 citationCount（有效引用数）、citationPrecision、citationRecall 和 grounded：citationCount 是从回答中解析到的有效 [Reference N] 引用个数；citationPrecision = |引用∩期望| / (有效引用数 + 越界引用数)，越界引用即编造引用，会拉低精确率；citationRecall = |引用∩期望| / |期望|；grounded 表示有至少一个有效引用且无越界引用。

回答类指标（Answer Keyword Hit、引用精确率、引用召回率、grounded）在未生成回答时为 NULL，表示不适用；汇总时只对非 NULL 的 case 求平均，生成回答但没有引用则记 0。eval_case.expectedAnswer 是预留字段，当前不参与任何评分。
