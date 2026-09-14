# RAG Evaluation 评测模块

本文说明评测模块的能力、指标定义与评测流程。完整的实验条件与逐项解读见 [`evaluation/mybatis-eval/RESULTS.md`](../evaluation/mybatis-eval/RESULTS.md)。
其他文档：[架构](architecture.md) · [数据模型](data-model.md) · [API](api.md) · [配置](configuration.md) · [检索](retrieval.md) · [返回根 README](../README.md)

RAG Evaluation 模块用于评估不同 RAG 检索策略在同一评测数据集上的表现。

该模块支持：

- 创建评测数据集；
- 添加评测问题；
- 为每个问题配置期望召回的 chunkId 和期望关键词；
- 启动一次评测运行；
- 指定检索模式、topK、candidateK 和是否生成回答；
- 保存每个 case 的检索结果和回答结果；
- 汇总整次评测运行的指标；
- 对多个评测 run 进行横向比较。

## 评测指标

当前支持的评测指标包括：

| 指标                 | 说明                                                         |
| -------------------- | ------------------------------------------------------------ |
| `Recall@K`           | 前 K 个结果召回了多少期望 chunk。                            |
| `Hit@K`              | 前 K 个结果中是否命中至少一个期望 chunk。                    |
| `MRR`                | 第一个正确 chunk 的倒数排名。                                |
| `Answer Keyword Hit` | 回答中命中期望关键词的比例。                                 |
| `Citation Precision` | 引用精确率 `|引用 ∩ 期望| / (有效引用数 + 越界引用数)`。      |
| `Citation Recall`    | 引用召回率 `|引用 ∩ 期望| / |期望|`。                        |
| `Grounded`           | 回答至少给出一个有效引用且没有越界引用记为 1，否则记为 0。   |
| `Average Latency`    | 平均检索或问答耗时。                                         |

### 引用指标的实现方式

prompt 中的上下文按 `[Reference 1]` 到 `[Reference N]` 编号并要求模型标注引用；评测时从回答中解析 `[Reference N]`，按编号映射回本次检索结果的 chunkId（编号 N 对应第 N 条），再与期望 chunk 比对：

- `citationCount`：解析到的有效引用个数；
- 引用编号越界（超出上下文范围，即编造引用）会计入精确率分母，从而拉低引用精确率；
- `grounded` 表示"引用未编造"，不声称回答内容被知识库真正支撑。

### NULL 语义

回答类指标（`Answer Keyword Hit`、引用精确率、引用召回率、`Grounded`）在未生成回答时为 NULL（不适用），汇总时只对非 NULL 的 case 求平均；生成了回答但没有任何引用则记为 0，属于可度量的质量失败。检索类指标不受影响。

`expectedAnswer` 是预留字段，当前不参与任何评分，仅做存储和展示。

## 评测结果

基于 MyBatis 3.5.19 官方中文文档（8 篇，约 12.4 万字）构建评测集，26 个 EvalCase 由人工标注期望 chunk，两个知识空间分别以定长切分与结构感知切分导入同一语料。检索参数 `topK=5`、`candidateK=20`，关闭回答生成，只评测检索质量。

**分块策略对比**（结构感知 − 定长）：

| 模式 | ΔRecall@K | ΔHit@K | ΔMRR |
|---|---:|---:|---:|
| VECTOR_ONLY | +0.026 | +0.038 | −0.033 |
| KEYWORD_ONLY | −0.019 | −0.038 | **+0.162** |
| HYBRID | **+0.043** | ±0 | +0.016 |
| HYBRID_RERANK | −0.014 | ±0 | **+0.061** |

结构感知切分在召回与排序上整体占优，HYBRID 的 Recall@K 提升最明显；代价是 chunk 数增加约 18%，且纯关键词检索的召回广度略有下降（chunk 更细，答案易被拆散），但其 MRR 提升显著——切分时附加的标题路径使 chunk 自带上下文。

**检索策略对比**：

| 模式 | Recall@K（定长/结构） | MRR（定长/结构） | 平均延迟（定长/结构） |
|---|---|---|---|
| VECTOR_ONLY | 0.8045 / 0.8301 | 0.7917 / 0.7590 | 1137 / 1025 ms |
| KEYWORD_ONLY | 0.7500 / 0.7308 | 0.5910 / 0.7532 | **74 / 24 ms** |
| HYBRID | 0.7917 / 0.8349 | 0.8186 / 0.8346 | 1470 / 960 ms |
| HYBRID_RERANK | **0.9647 / 0.9503** | **0.9051 / 0.9658** | 29177 / 57180 ms |

- 向量检索是更强的单一基线，关键词检索的优势在延迟（快 15–43 倍），适合含精确术语的查询；
- HYBRID 的收益主要体现在排序质量（MRR 优于 VECTOR_ONLY），但不必然提升召回覆盖；
- HYBRID_RERANK 效果最好（两种切分下 Hit@K 均为 1.0000，MRR 最高），代价是延迟——本实验的 reranker 为本地 CPU 部署，单次重排约 30 秒，GPU 部署可大幅降低该开销。

> 样本量为 26 题，结论具备方向性参考价值，不适合对小幅差异做强断言。评测集偏语义理解型问题，对关键词检索不完全公平。回答质量（引用精确率/召回率、grounded）需要在开启回答生成的实验中评测，本轮未涉及。

## 评测流程

1. **导入语料**：向知识空间导入用于评测的知识库文档，等待索引任务执行完成，确认文档状态为 `ACTIVE`；
2. **构造评测集**：从实际数据中取到真实的 chunkId，建立 EvalDataset 并添加 EvalCase（问题 + 期望 chunkId + 期望关键词）；
3. **分别评测**：对同一数据集分别以 `VECTOR_ONLY`、`KEYWORD_ONLY`、`HYBRID`、`HYBRID_RERANK` 四种模式启动 EvalRun；
4. **对比**：调用 `POST /api/rag/eval/runs/compare` 对多个 run 横向比较，再依据指标差异调整检索链路参数。

可执行脚本见 [`evaluation/eval-http/`](../evaluation/eval-http/)，接口清单见 [API 文档](api.md#rag-evaluation-接口)。
