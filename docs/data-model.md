# 数据模型

本文说明系统的核心数据表、文档状态机，以及索引任务的类型、状态、去重与可靠性设计。
其他文档：[架构](architecture.md) · [API](api.md) · [配置](configuration.md) · [检索](retrieval.md) · [评测](evaluation.md) · [返回根 README](../README.md)

## 核心数据表

当前系统使用以下核心数据表：

```text
kb_space
kb_document
kb_chunk
kb_index_task
qa_session
qa_message
qa_reference
eval_dataset
eval_case
eval_run
eval_case_result
```

## 知识库相关表

### `kb_space`

用于保存知识空间信息。一个知识空间可以理解为一组相关文档的集合，例如某个项目资料库、课程资料库或业务知识库。

### `kb_document`

用于保存文档信息，包括文档所属知识空间、标题、正文内容、状态和时间信息等。

文档状态共四态：

```text
0 = INVALID   被删除或更新后的旧文档，记录保留但不参与检索
1 = ACTIVE    全部 chunk 索引构建成功，生效、可检索
2 = INDEXING  新建或索引构建中，尚未生效
3 = FAILED    索引构建存在失败，可重试，尚未生效
```

检索只返回 `ACTIVE` 状态的文档；检索出口还会以 MySQL 为准做一次有效性校验（chunk 存在且 status=1、其所属文档为 `ACTIVE`、所属知识空间正常）。

### `kb_chunk`

用于保存文档切分后的文本片段。RAG 检索通常不会直接以整篇长文档作为向量化单位，而是先将文档拆分为较小的 chunk，再分别进行向量化和检索。

主要字段包括：

```text
id
document_id
space_id
chunk_index
content
char_count
embedding_status
vector_id
status
created_at
updated_at
```

其中，`embedding_status` 用于记录 chunk 的向量化状态，`vector_id` 用于保存该 chunk 写入 Qdrant 后对应的向量 ID，`chunk_index` 用于记录 chunk 在原文档中的顺序。

### `kb_index_task`

用于保存持久化索引任务记录，使文档的 Qdrant 向量索引与 Elasticsearch 关键词索引的构建/清理过程可以被提交、调度、重试和查询。

任务类型：

```text
BUILD_INDEX   为新文档建立 Qdrant + ES 索引
DELETE_INDEX  删除旧文档的 Qdrant + ES 索引
REPAIR_INDEX  对账发现索引缺失后的补建
```

文档更新等价于 `DELETE_INDEX`（旧文档）+ `BUILD_INDEX`（新文档）。

任务状态：

```text
0 = PENDING     待执行
1 = RUNNING     执行中
2 = RETRY_WAIT  等待重试（记录 next_retry_at）
3 = SUCCESS     成功（终态）
4 = FAILED      重试耗尽或索引构建中止（终态，可人工重试）
```

去重机制：同一文档的"写索引类"任务互斥——`BUILD_INDEX` 与 `REPAIR_INDEX` 共用 `BUILD` 分组，`DELETE_INDEX` 独立成组。由数据库生成列 `dedup_group`、`active_flag` 与唯一索引 `uk_task_active_dedup(document_id, dedup_group, active_flag)` 在数据库层保证：活跃任务（`PENDING` / `RUNNING` / `RETRY_WAIT`）在每个分组内至多一条，终态行的 `active_flag` 为 `NULL`，不受唯一约束限制。

可靠性：

- 任务落库，应用重启后自动续跑；
- 失败按指数退避自动重试（base × 2^n，上限 60s，默认 5 次后置 `FAILED`）；
- `RUNNING` 任务超时（默认 30min）自动回收重排；
- 提供人工重试 `FAILED` 任务的接口；
- 周期对账默认每天 03:00（`rag.reconciliation.cron`）执行，清理 Qdrant / Elasticsearch 孤儿索引，并为 `ACTIVE` 文档缺失的索引登记 `REPAIR_INDEX`；Qdrant 与 Elasticsearch 任一缺失即视为索引缺失。

索引构建期间文档若被删除或更新（置为 `INVALID`），任务收尾时以锁定读（`SELECT ... FOR SHARE`）确认；若已失效，则清理本次写入的 Qdrant / ES 索引并中止，任务直接置为 `FAILED` 终态且不重试。

## 问答历史相关表

### `qa_session`

用于保存一次问答会话的基本信息。通过会话表，可以将同一轮或同一主题下的问答记录组织起来。

### `qa_message`

用于保存用户问题和 AI 回答内容。该表记录一次问答中的用户输入、模型输出以及相关时间信息。

### `qa_reference`

用于保存回答引用的知识库 chunk 信息。通过引用记录，可以追踪某个回答具体基于哪些知识片段生成，便于后续检查回答依据和调试检索效果。

## Evaluation 相关表

### `eval_dataset`

用于保存 RAG 评测数据集。一个评测数据集通常对应某个知识空间，用于管理一组评测问题。

### `eval_case`

用于保存单个评测问题，包括问题文本、参考答案、期望召回的 chunkId 列表、期望关键词和难度等级等信息。

### `eval_run`

用于保存一次评测运行的配置和汇总结果，包括检索模式、topK、candidateK、是否生成回答、总 case 数、成功数量、失败数量，以及 Recall@K、Hit@K、MRR、回答关键词命中率、引用精确率、引用召回率、grounded 比例和平均耗时等整体指标。

### `eval_case_result`

用于保存每个评测 case 的执行结果，包括实际召回 chunkId、期望 chunkId、Recall@K、Hit@K、MRR、回答关键词命中率、引用个数、引用精确率、引用召回率、grounded 和耗时等信息。
