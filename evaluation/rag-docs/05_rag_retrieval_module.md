# RAG 检索模块说明

RAG 检索模块提供统一的知识库检索入口，主要由 RagRetrievalController、RagRetrievalService、RagRetrievalServiceImpl、SearchChunksRequest、SearchChunkResponse 和 RetrievalMode 构成。对外接口为 POST /api/rag/search。

SearchChunksRequest 包含 spaceId、query、retrievalMode、topK 和 candidateK。spaceId 用于限定知识库范围，query 是用户查询内容，topK 表示最终返回的 chunk 条数，candidateK 表示混合检索中每一路的召回深度，实际取值会取 max(candidateK, topK)，默认 20。retrievalMode 支持 VECTOR_ONLY、KEYWORD_ONLY、HYBRID 和 HYBRID_RERANK。向量检索的 topK 有上限约束，默认为 50。

VECTOR_ONLY 模式只使用 Qdrant 向量检索。系统将 query 转换为 embedding 后，在 Qdrant 中根据 space_id metadata 过滤指定知识库空间，并返回相似 chunk。向量检索适合自然语言语义问题，例如“系统如何完成一次 RAG 问答”。

KEYWORD_ONLY 模式使用 Elasticsearch 关键词检索，并借助 smartcn 中文分词处理中文文本。系统按分词结果在关键词索引中查找匹配片段并计算 keywordScore，再按 spaceId 过滤知识库范围。关键词检索适合类名、接口路径、方法名、字段名等精确符号，例如 RagRetrievalServiceImpl、/api/rag/search 和 SearchChunksRequest。

HYBRID 模式同时执行向量检索和关键词检索，两路各召回 max(candidateK, topK) 个候选，再按 chunkId 合并去重。融合采用等权 RRF：每一路按排名贡献 1/(RRF_K + rank + 1)，其中 RRF_K 为 60、rank 从 0 起算；同一个 chunk 在多路中的贡献相加后降序排列。跨路命中的 chunk，其 retrievalSource 记为 BOTH；只被向量检索命中记为 VECTOR；只被关键词检索命中记为 KEYWORD。检索只返回状态为 ACTIVE 的文档下的 chunk，出口另有以 MySQL 为准的有效性校验。

HYBRID_RERANK 模式在混合召回结果之上调用 TEI rerank 服务（配置 rag.rerank.base-url）做重排序，进入重排的候选数量默认上限为 30，由 rag.retrieval.rerank-candidate-top-m 控制，最终返回 topK 条。

检索结果统一封装为 SearchChunkResponse。该对象包含 chunkId、documentId、spaceId、chunkIndex、content、score、vectorScore、keywordScore、hybridScore、rerankScore、finalScore 和 retrievalSource 等字段。vectorScore 和 keywordScore 是各路检索的原始分数；hybridScore 和 finalScore 在混合模式下为 RRF 融合分，在重排模式下为重排分。
