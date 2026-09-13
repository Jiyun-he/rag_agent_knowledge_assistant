# 持久化索引任务模块说明

持久化索引任务模块负责在文档创建、更新、删除之后，异步维护该文档在 Qdrant 中的向量索引和 Elasticsearch 中的关键词索引。由于 embedding 计算与索引写入都依赖外部服务，耗时比普通数据库操作更长，因此项目不再在请求线程内直接完成索引，而是把索引工作登记为持久化任务，由后台 worker 轮询执行，接口只负责登记并立即返回任务详情。

模块由若干组件分工：IndexTaskController 暴露对外接口，IndexTaskService 及其实现负责登记、查询与重试，IndexTaskWorker 负责异步执行，IndexCleanupService 及其实现负责清理失效文档的索引，IndexReconciliationService 及其实现负责索引对账。任务信息保存在 kb_index_task 表中，由 KbIndexTask 实体表示，对外由 IndexTaskResponse 返回，单次索引构建结果由 IndexBuildResult 表示，对账结果由 ReconcileResult 表示。任务类型和任务状态分别由 TaskType 和 TaskStatus 枚举定义。

任务类型有三种：BUILD_INDEX 为新文档建立索引，DELETE_INDEX 删除文档索引，REPAIR_INDEX 在对账发现索引缺失后补建。文档更新等价于对旧文档登记 DELETE_INDEX、对新文档登记 BUILD_INDEX。

任务状态包括 0 PENDING、1 RUNNING、2 RETRY_WAIT、3 SUCCESS 和 4 FAILED。worker 通过条件更新抢占任务，抢占成功后任务由 PENDING 进入 RUNNING；执行成功进入终态 SUCCESS；执行失败则按指数退避转入 RETRY_WAIT，等待 nextRetryAt 到达后再次执行，重试耗尽后进入终态 FAILED。终态 FAILED 的任务可以通过人工重试接口重置为 PENDING 并重新入队。对于进程异常退出遗留的 RUNNING 任务，系统会做超时回收，将其置回 PENDING、重试计数加一后重新排队。

任务去重由数据库层保证。kb_index_task 通过生成列计算去重分组：BUILD_INDEX 和 REPAIR_INDEX 都属于“写索引”，共用 BUILD 分组；DELETE_INDEX 属于清理操作，独立成组。再配合唯一索引约束同一文档、同一分组下最多存在一条活跃任务（PENDING、RUNNING 或 RETRY_WAIT），从而保证同一文档的写索引任务互斥，避免并发写入互相覆盖。

索引构建由 KnowledgeEmbeddingService 承担。BUILD_INDEX 只处理 embeddingStatus 为 0（待处理）或 2（失败）的 chunk，已成功的 chunk 不重复处理，因此天然支持失败重试时的部分成功续传；REPAIR_INDEX 则忽略 embeddingStatus，对文档的全部正常 chunk 做全量重写。构建时系统为每个 chunk 生成稳定 vectorId，在 metadata 中写入 source、chunk_id、document_id、space_id 和 chunk_index，将向量写入 Qdrant，同时把 chunk 写入 Elasticsearch 关键词索引。成功时 KbChunk 的 embeddingStatus 置为 1 并写入 vectorId，失败时置为 2。任务执行完成后统计 totalChunkCount、pendingChunkCount、successCount、failedCount 和 skippedCount，并更新任务的最终状态。

除按需任务外，系统还会周期性执行索引对账，默认每天 03:00 触发。对账会清理 Qdrant 和 Elasticsearch 中对应的孤儿索引，并为状态为 ACTIVE 但索引缺失的文档登记 REPAIR_INDEX。

对外接口包括：POST /api/rag/documents/{documentId}/index 手动触发 BUILD_INDEX；GET /api/rag/index-tasks/{taskId} 查询任务；GET /api/rag/documents/{documentId}/index-tasks 查询某文档的任务列表；POST /api/rag/index-tasks/{taskId}/retry 人工重试 FAILED 任务；POST /api/rag/index/reconcile 和 POST /api/rag/index/documents/{documentId}/reconcile 人工触发对账；POST /api/rag/index/rebuild 重建 Elasticsearch 关键词索引，它只改 Elasticsearch，不涉及 Qdrant。
