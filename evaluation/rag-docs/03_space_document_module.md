# 知识库空间与文档管理模块说明

知识库空间模块负责管理多个知识库空间。一个 space 可以对应一个项目知识库、一组企业制度文档、一门课程资料或一个业务场景。KnowledgeSpaceController 提供 /api/spaces 下的创建、查询、更新和删除接口。KnowledgeSpaceServiceImpl 负责处理同名校验、状态过滤和软删除逻辑。

文档管理模块负责维护知识库中的原始文档。KnowledgeDocumentController 提供 /api/documents 下的文档创建、查询、更新、删除和 chunk 查询接口。CreateDocumentRequest 包含 spaceId、title、content、sourceType 和 sourceUri。创建文档时，系统会先校验 spaceId 是否存在，然后写入 KbDocument，再调用 TextChunker 对 content 进行切分，并将生成的片段写入 kb_chunk 表。

文档创建并不是简单保存一条 document 记录。因为 RAG 检索的基本单位是 chunk，而不是整篇文档，所以文档写入后必须生成 KbChunk。每个 chunk 会记录 documentId、spaceId、chunkIndex、content、charCount、embeddingStatus 和 status。初始 embeddingStatus 为 0，表示该 chunk 尚未向量化。

文档状态共有 INVALID、ACTIVE、INDEXING 和 FAILED 四态。新建文档的初始状态为 INDEXING，此时尚未生效、不可被检索；当文档下全部 chunk 索引成功后转为 ACTIVE，才参与检索；只要存在失败的 chunk，文档即为 FAILED，可以重试；被更新或删除的旧文档转为 INVALID，但永不物理删除。检索只返回 ACTIVE 文档，出口另有以 MySQL 为准的有效性校验。

文档创建、更新和删除都不在请求线程内同步完成索引操作，而是在事务提交后登记索引任务，由 worker 异步执行。创建文档登记 BUILD_INDEX；删除文档登记 DELETE_INDEX；更新文档登记 DELETE_INDEX(旧文档) 加 BUILD_INDEX(新文档)。这里所说的删除采用软删除方式，将 document.status 置为 INVALID，同时将关联 chunk.status 置为 0。更新时，系统会更新 KbDocument 的内容，并删除该 documentId 下旧的 chunk，再按照新内容重新切分和写入 chunk，这样可以避免文档正文与 chunk 内容不一致。
