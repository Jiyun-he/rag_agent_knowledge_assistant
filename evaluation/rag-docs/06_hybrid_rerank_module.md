# Hybrid Search 与 Rerank 模块说明

Hybrid Search + Rerank 是本项目在基础向量检索之上的增强检索模块。它的目标是同时利用语义召回和关键词召回，改善技术文档场景下只依赖向量检索可能出现的类名、接口名、方法名命中不稳定问题。

HYBRID 模式会先执行向量检索，召回 max(candidateK, topK) 个候选 chunk；同时执行关键词检索，也召回同样深度的候选 chunk。其中关键词检索基于 Elasticsearch 与 smartcn 中文分词。随后系统按 chunkId 对两组候选结果合并去重。如果一个 chunk 同时被向量检索和关键词检索命中，则 retrievalSource 记为 BOTH；如果只被向量检索命中，则为 VECTOR；如果只被关键词检索命中，则为 KEYWORD。

合并后的融合采用等权 RRF，也就是 Reciprocal Rank Fusion。每一路只按排名贡献分数，不引入任何权重系数：第 rank 位（rank 从 0 起算）的贡献为 1/(RRF_K + rank + 1)，其中 RRF_K 为 60。同一个 chunk 若被多路命中，则把各路贡献相加得到 hybridScore，最后按 hybridScore 降序排列。由于 RRF 只依赖排名，请求中不提供 vectorWeight 或 keywordWeight 之类的权重参数。

HYBRID_RERANK 模式会在 HYBRID 结果的候选集合上继续做神经网络重排序。系统取融合后的前若干条候选作为重排输入，数量上限默认 30，由配置 rag.retrieval.rerank-candidate-top-m 控制；然后将 query 与候选文本一起发送给 TEI 的 /rerank 服务（由配置 rag.rerank.base-url 指定），得到重排分数 rerankScore，作为 finalScore 后降序返回 topK 条结果。

该模块的重排能力由外部 TEI 服务提供，应用侧只负责组织重排候选、发起调用并把返回分数回填到检索结果中。与等权 RRF 融合不同，重排阶段会对 query 与候选文本做联合打分。
