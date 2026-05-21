# 异步向量化模块说明

异步向量化模块负责将文档 chunk 转换为 embedding 向量，并写入 Qdrant。由于 embedding 调用可能涉及外部模型接口，耗时比普通数据库操作更长，因此项目通过异步任务方式执行向量化。

EmbeddingTaskController 提供 POST /api/rag/documents/{documentId}/vectorize-async 接口，用于创建并启动文档向量化任务。任务信息保存在 kb_embedding_task 表中，由 KbEmbeddingTask 表示。任务创建后，EmbeddingTaskServiceImpl 会先写入 pending 状态的任务记录，然后通过 runTaskAsync 异步执行实际向量化逻辑。

实际向量化由 KnowledgeEmbeddingServiceImpl.vectorizeDocument(documentId) 完成。该方法会查询指定文档下 status = 1 且 embeddingStatus = 0 的 chunk。对于每个 pending chunk，系统会生成稳定 vectorId，构造 Spring AI Document，并在 metadata 中写入 source、chunk_id、document_id、space_id 和 chunk_index。随后调用 VectorStore.add 将向量写入 Qdrant。

向量化成功后，系统会更新 KbChunk 的 embeddingStatus 为 1，并写入 vectorId。向量化失败时，embeddingStatus 会被设置为 2。任务执行完成后，系统会统计 totalChunkCount、pendingChunkCount、successCount、failedCount 和 skippedCount，并更新 KbEmbeddingTask 的最终状态。success 表示全部成功，partial_success 表示部分成功，failed 表示任务失败。
