# Hybrid Search 与 Rerank 模块说明

Hybrid Search + Rerank 是本项目在基础向量检索之上的增强检索模块。它的目标是同时利用语义召回和关键词召回，改善技术文档场景下只依赖向量检索可能出现的类名、接口名、方法名命中不稳定问题。

HYBRID 模式会先执行向量检索，召回 candidateK 个候选 chunk；同时执行关键词检索，再召回 candidateK 个候选 chunk。随后系统按 chunkId 对两组候选结果合并去重。如果一个 chunk 同时被向量检索和关键词检索命中，则 retrievalSource 记为 BOTH；如果只被向量检索命中，则为 VECTOR；如果只被关键词检索命中，则为 KEYWORD。

合并后，系统会计算 normalizedVectorScore 和 normalizedKeywordScore，并按照权重融合得到 hybridScore。当前融合公式为 hybridScore = vectorWeight * normalizedVectorScore + keywordWeight * normalizedKeywordScore。默认 vectorWeight 为 0.7，keywordWeight 为 0.3。

HYBRID_RERANK 模式会在 HYBRID 结果基础上继续进行规则重排序。当前规则会计算 exactTermScore 和 symbolScore。exactTermScore 表示普通精确词命中程度，symbolScore 更关注类名、方法名、接口路径、Request、Response、Service、Controller 等技术符号。最终 rerankScore 会结合 hybridScore、exactTermScore 和 symbolScore，得到 finalScore 并按降序返回 topK 结果。

该模块当前属于规则型 rerank，而不是 cross-encoder 或 LLM rerank。它的特点是实现简单、成本低、可解释，适合第一版工程验证。
