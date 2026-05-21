# RAG 检索模块说明

RAG 检索模块提供统一的知识库检索入口，主要由 RagRetrievalController、RagRetrievalService、RagRetrievalServiceImpl、SearchChunksRequest、SearchChunkResponse 和 RetrievalMode 构成。对外接口为 POST /api/rag/search。

SearchChunksRequest 包含 spaceId、query、retrievalMode、topK、candidateK、vectorWeight 和 keywordWeight。spaceId 用于限定知识库范围，query 是用户查询内容，topK 表示最终返回的 chunk 数量，candidateK 表示混合召回阶段的候选数量。retrievalMode 支持 VECTOR_ONLY、KEYWORD_ONLY、HYBRID 和 HYBRID_RERANK。

VECTOR_ONLY 模式只使用 Qdrant 向量检索。系统将 query 转换为 embedding 后，在 Qdrant 中根据 space_id metadata 过滤指定知识库空间，并返回相似 chunk。向量检索适合自然语言语义问题，例如“系统如何完成一次 RAG 问答”。

KEYWORD_ONLY 模式使用 MySQL 关键词检索。系统会从 query 中提取 terms，通过 kb_chunk.content 的 LIKE 条件查找匹配片段，并计算 keywordScore。关键词检索适合类名、接口路径、方法名、字段名等精确符号，例如 RagRetrievalServiceImpl、/api/rag/search 和 SearchChunksRequest。

检索结果统一封装为 SearchChunkResponse。该对象包含 chunkId、documentId、spaceId、chunkIndex、content、score、vectorScore、keywordScore、hybridScore、rerankScore、finalScore 和 retrievalSource 等字段。
