# 检索增强：Hybrid Search + Rerank

本文说明系统支持的检索模式、检索参数语义、RRF 融合算法与分数含义。
其他文档：[架构](architecture.md) · [数据模型](data-model.md) · [API](api.md) · [配置](configuration.md) · [评测](evaluation.md) · [返回根 README](../README.md)

项目在基础向量检索能力之上，进一步扩展了多种检索模式，用于提升 RAG 场景下的召回质量和可解释性。

## 检索模式

| 检索模式        | 说明                                                         |
| --------------- | ------------------------------------------------------------ |
| `VECTOR_ONLY`   | 仅使用向量检索，从 Qdrant 中召回相似 chunk。                 |
| `KEYWORD_ONLY`  | 仅使用关键词检索，基于 Elasticsearch + smartcn 中文分词，适合类名、方法名、接口路径、技术术语等精确匹配场景。 |
| `HYBRID`        | 同时执行向量召回和关键词召回，按等权 RRF 融合两路结果。      |
| `HYBRID_RERANK` | 在混合召回的基础上调用 TEI `/rerank` 重排模型进行重排序。    |

## 检索参数

检索请求支持的主要参数包括：

```text
retrievalMode
query
topK
candidateK
```

参数说明：

- `topK`：最终返回的结果条数。
- `candidateK`：混合检索中**每一路召回的深度**，即向量检索与关键词检索各自召回多少条候选再融合；实际取 `max(candidateK, topK)`，请求未指定时默认 20。
- `HYBRID_RERANK` 送入重排模型的候选数上限为 `max(rag.retrieval.rerank-candidate-top-m, topK)`，默认 30。

## 融合算法：等权 RRF

融合算法为**等权 RRF（Reciprocal Rank Fusion）**：每个检索器按自身排名贡献 `1 / (RRF_K + rank + 1)` 分（`RRF_K = 60`，rank 从 0 起），同一 chunk 在两路的分数相加后按降序排序。两路权重相等，检索请求不提供 `vectorWeight` / `keywordWeight` 参数。

## 分数字段

检索结果会返回以下分数信息：

```text
vectorScore
keywordScore
hybridScore
rerankScore
finalScore
retrievalSource
```

分数语义：`vectorScore` / `keywordScore` 为各路原始分；`hybridScore` / `finalScore` 在混合模式下为 RRF 分数；`HYBRID_RERANK` 模式下为 TEI 重排模型给出的重排分数，并写入 `rerankScore` / `finalScore`。`retrievalSource` 用于表示结果来源，取值为 `VECTOR`、`KEYWORD` 或 `BOTH`。这些字段便于分析不同 chunk 的召回来源、排序依据和检索策略差异。

## 实测表现

四种模式在 MyBatis 中文文档语料上的召回、排序与延迟对比，见[评测结果](../evaluation/mybatis-eval/RESULTS.md)。
