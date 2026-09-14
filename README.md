# RAG Agent Knowledge Assistant

`rag_agent_knowledge_assistant` 是一个基于 Spring Boot 构建的 RAG（Retrieval-Augmented Generation，检索增强生成）知识库问答后端系统。项目围绕知识空间、文档管理、文本切分、Embedding 向量化、Qdrant 向量检索、Elasticsearch 关键词检索、Hybrid Search + Rerank、RAG 问答、问答历史保存、引用追踪和 RAG 评测，构建了一条较完整的知识库问答系统后端链路。

本项目面向真实 AI 应用开发场景，重点实现结构化数据管理、向量数据库接入、语义检索、关键词召回、混合检索、检索重排序、持久化索引任务调度、问答结果来源追踪和 RAG 策略评测等工程化能力。系统可以作为知识库问答、企业内部文档助手、项目资料检索助手、RAG 检索策略实验平台等应用的后端基础。

## 功能特性

- 知识空间管理
- 文档创建、查询、更新和删除
- 文档内容自动切分为文本片段
- MySQL 存储知识空间、文档、chunk、索引任务、问答历史和评测记录
- 文档状态四态管理（INVALID / ACTIVE / INDEXING / FAILED）
- OpenAI-compatible Embedding API 接入
- OpenAI-compatible Chat API 接入
- Qdrant 向量数据库接入
- Elasticsearch 关键词索引（smartcn 中文分词）
- 持久化索引任务：文档创建/更新/删除在事务提交后（afterCommit）登记任务，由 worker 调度执行
- 索引任务失败指数退避重试、RUNNING 超时回收、人工重试
- 周期索引对账：清理 Qdrant / Elasticsearch 孤儿索引，并为索引缺失的 ACTIVE 文档补建
- 基于 Qdrant 的向量语义检索
- 基于 Elasticsearch 的关键词检索
- Hybrid Search：向量召回与关键词召回按等权 RRF 融合
- TEI Rerank：对混合召回结果调用重排模型重排序
- 支持 `VECTOR_ONLY`、`KEYWORD_ONLY`、`HYBRID`、`HYBRID_RERANK` 多种检索模式
- 检索结果返回向量分数、关键词分数、融合分数、重排序分数和最终排序分数
- 基于检索结果的 RAG 问答接口
- 问答结果返回来源片段，便于追踪回答依据
- 问答历史保存与引用记录
- RAG Evaluation 评测数据集、评测 case、评测 run 和 case 结果保存
- 支持 Recall@K、Hit@K、MRR、Answer Keyword Hit、引用精确率、引用召回率、grounded、Average Latency 等评测指标
- 支持多个评测 run 的横向对比
- 统一 API 返回格式
- 全局异常处理

## 技术栈

| 模块           | 技术                                                    |
| -------------- | ------------------------------------------------------- |
| 后端框架       | Spring Boot                                             |
| ORM 框架       | MyBatis-Plus                                            |
| 结构化数据库   | MySQL                                                   |
| 向量数据库     | Qdrant                                                  |
| 关键词索引     | Elasticsearch（smartcn 中文分词）                       |
| Embedding 服务 | OpenAI-compatible Embedding API                         |
| Chat 服务      | OpenAI-compatible Chat API                              |
| Rerank 服务    | TEI（Text Embeddings Inference）`/rerank`               |
| 异步处理       | Spring `@Async` + `@Scheduled` + 持久化任务表           |
| 接口测试       | IntelliJ IDEA HTTP Client / REST 工具                   |
| 容器管理       | Docker Desktop                                          |
| 构建工具       | Maven                                                   |

## 系统架构

项目采用典型的 Spring Boot 分层架构，将接口层、业务层、数据访问层和外部服务调用进行拆分。文档索引工作在事务提交后登记为持久化任务，由定时 worker 调度执行。

```text
Client / API Tester
        |
        v
Controller Layer
        |
        v
Service Layer
        |
        +-- MySQL / MyBatis-Plus     结构化业务数据、索引任务、问答历史、评测记录
        +-- Embedding API            文本向量化
        +-- Chat API                 回答生成
        +-- Qdrant                   向量检索
        +-- Elasticsearch            关键词检索（smartcn 中文分词）
        +-- TEI Rerank               混合召回结果重排序
```

主包路径为：

```text
com.nuaa.ragagent
```

主要包结构如下：

| 包名                       | 作用                                              |
| -------------------------- | ------------------------------------------------- |
| `controller`               | 对外提供 REST API                                 |
| `service` / `service.impl` | 实现核心业务逻辑                                  |
| `mapper`                   | 通过 MyBatis-Plus 访问 MySQL                      |
| `entity`                   | 映射数据库表结构                                  |
| `request`                  | 封装接口请求参数                                  |
| `response`                 | 封装接口响应结果                                  |
| `common`                   | 提供统一响应结构                                  |
| `exception`                | 处理业务异常和全局异常                            |
| `util`                     | 提供文本切分、RAG prompt 构造、引用解析等工具逻辑 |
| `config`                   | 提供异步线程池、定时调度、AI 客户端等配置         |
| `enums`                    | 提供检索模式、任务类型/状态、文档状态等枚举定义   |

## 核心流程

系统的主要 RAG 工作流程如下：

```text
创建知识空间
        ↓
创建文档
        ↓
文档内容切分为 chunks
        ↓
事务提交后（afterCommit）登记 BUILD_INDEX 任务
        ↓
worker 调度任务：调用 Embedding API 生成向量
        ↓
将向量写入 Qdrant，并将 chunk 写入 Elasticsearch 关键词索引
        ↓
锁定读确认文档未失效后，将文档状态置为 ACTIVE
        ↓
根据用户问题进行检索
        ↓
按检索模式执行向量检索、关键词检索、RRF 混合融合或模型重排序
        ↓
获取相关知识片段
        ↓
基于检索上下文生成回答
        ↓
返回回答内容和来源片段
        ↓
保存问答历史与引用记录
```

通过该流程，系统能够同时保留文档的结构化管理能力、面向语义检索的向量索引能力、面向精确术语的关键词召回能力，以及面向回答可追溯性的引用记录能力。只有状态为 `ACTIVE` 的文档才会进入检索结果，检索出口还会以 MySQL 为准做一次有效性校验，避免返回已删除或已失效的内容。

## Hybrid Search + Rerank 检索增强

项目在基础向量检索能力之上，进一步扩展了多种检索模式，用于提升 RAG 场景下的召回质量和可解释性。

当前支持的检索模式包括：

| 检索模式        | 说明                                                         |
| --------------- | ------------------------------------------------------------ |
| `VECTOR_ONLY`   | 仅使用向量检索，从 Qdrant 中召回相似 chunk。                 |
| `KEYWORD_ONLY`  | 仅使用关键词检索，基于 Elasticsearch + smartcn 中文分词，适合类名、方法名、接口路径、技术术语等精确匹配场景。 |
| `HYBRID`        | 同时执行向量召回和关键词召回，按等权 RRF 融合两路结果。      |
| `HYBRID_RERANK` | 在混合召回的基础上调用 TEI `/rerank` 重排模型进行重排序。    |

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

融合算法为**等权 RRF（Reciprocal Rank Fusion）**：每个检索器按自身排名贡献 `1 / (RRF_K + rank + 1)` 分（`RRF_K = 60`，rank 从 0 起），同一 chunk 在两路的分数相加后按降序排序。两路权重相等，检索请求不提供 `vectorWeight` / `keywordWeight` 参数。

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

## RAG Evaluation 评测模块

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

引用指标的实现方式：prompt 中的上下文按 `[Reference 1]` 到 `[Reference N]` 编号并要求模型标注引用；评测时从回答中解析 `[Reference N]`，按编号映射回本次检索结果的 chunkId（编号 N 对应第 N 条），再与期望 chunk 比对：

- `citationCount`：解析到的有效引用个数；
- 引用编号越界（超出上下文范围，即编造引用）会计入精确率分母，从而拉低引用精确率；
- `grounded` 表示"引用未编造"，不声称回答内容被知识库真正支撑。

关于 NULL 语义：回答类指标（`Answer Keyword Hit`、引用精确率、引用召回率、`Grounded`）在未生成回答时为 NULL（不适用），汇总时只对非 NULL 的 case 求平均；生成了回答但没有任何引用则记为 0，属于可度量的质量失败。检索类指标不受影响。

`expectedAnswer` 是预留字段，当前不参与任何评分，仅做存储和展示。

### 评测结果

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

完整实验条件、逐项解读与局限说明见 [`evaluation/mybatis-eval/RESULTS.md`](evaluation/mybatis-eval/RESULTS.md)。

> 样本量为 26 题，结论具备方向性参考价值，不适合对小幅差异做强断言。评测集偏语义理解型问题，对关键词检索不完全公平。回答质量（引用精确率/召回率、grounded）需要在开启回答生成的实验中评测，本轮未涉及。

### 评测流程

1. **导入语料**：向知识空间导入用于评测的知识库文档，等待索引任务执行完成，确认文档状态为 `ACTIVE`；
2. **构造评测集**：从实际数据中取到真实的 chunkId，建立 EvalDataset 并添加 EvalCase（问题 + 期望 chunkId + 期望关键词）；
3. **分别评测**：对同一数据集分别以 `VECTOR_ONLY`、`KEYWORD_ONLY`、`HYBRID`、`HYBRID_RERANK` 四种模式启动 EvalRun；
4. **对比**：调用 `POST /api/rag/eval/runs/compare` 对多个 run 横向比较，再依据指标差异调整检索链路参数。

## 数据模型

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

### `qa_session`

用于保存一次问答会话的基本信息。通过会话表，可以将同一轮或同一主题下的问答记录组织起来。

### `qa_message`

用于保存用户问题和 AI 回答内容。该表记录一次问答中的用户输入、模型输出以及相关时间信息。

### `qa_reference`

用于保存回答引用的知识库 chunk 信息。通过引用记录，可以追踪某个回答具体基于哪些知识片段生成，便于后续检查回答依据和调试检索效果。

### `eval_dataset`

用于保存 RAG 评测数据集。一个评测数据集通常对应某个知识空间，用于管理一组评测问题。

### `eval_case`

用于保存单个评测问题，包括问题文本、参考答案、期望召回的 chunkId 列表、期望关键词和难度等级等信息。

### `eval_run`

用于保存一次评测运行的配置和汇总结果，包括检索模式、topK、candidateK、是否生成回答、总 case 数、成功数量、失败数量，以及 Recall@K、Hit@K、MRR、回答关键词命中率、引用精确率、引用召回率、grounded 比例和平均耗时等整体指标。

### `eval_case_result`

用于保存每个评测 case 的执行结果，包括实际召回 chunkId、期望 chunkId、Recall@K、Hit@K、MRR、回答关键词命中率、引用个数、引用精确率、引用召回率、grounded 和耗时等信息。

## API 概览

### 健康检查接口

| Method | Path               | Description              |
| ------ | ------------------ | ------------------------ |
| GET    | `/api/health/ping` | 检查后端服务是否正常运行 |

### 知识空间接口

| Method | Path               | Description          |
| ------ | ------------------ | -------------------- |
| POST   | `/api/spaces`      | 创建新的知识空间     |
| GET    | `/api/spaces`      | 获取知识空间列表     |
| GET    | `/api/spaces/{id}` | 获取指定知识空间详情 |
| PUT    | `/api/spaces/{id}` | 更新指定知识空间信息 |
| DELETE | `/api/spaces/{id}` | 删除指定知识空间     |

### 文档接口

| Method | Path                       | Description                        |
| ------ | -------------------------- | ---------------------------------- |
| POST   | `/api/documents`           | 创建新的知识文档                   |
| GET    | `/api/documents`           | 获取知识文档列表，可按知识空间筛选 |
| GET    | `/api/documents/{id}`      | 获取指定知识文档详情               |
| PUT    | `/api/documents/{id}`      | 更新指定知识文档信息               |
| DELETE | `/api/documents/{id}`      | 删除指定知识文档                   |
| GET    | `/api/documents/{id}/chunks` | 获取指定文档切分后的 chunk 列表  |

### 索引任务接口

| Method | Path                                          | Description                                      |
| ------ | --------------------------------------------- | ------------------------------------------------ |
| POST   | `/api/rag/documents/{documentId}/index`       | 手动触发 BUILD_INDEX（登记任务，worker 异步执行） |
| GET    | `/api/rag/index-tasks/{taskId}`               | 根据任务 ID 查询索引任务状态                     |
| GET    | `/api/rag/documents/{documentId}/index-tasks` | 查询指定文档的索引任务记录                       |
| POST   | `/api/rag/index-tasks/{taskId}/retry`         | 人工重试 FAILED 任务                             |

### 索引维护接口

| Method | Path                                                | Description              |
| ------ | --------------------------------------------------- | ------------------------ |
| POST   | `/api/rag/index/reconcile`                          | 人工触发全量对账         |
| POST   | `/api/rag/index/documents/{documentId}/reconcile`   | 单文档对账               |
| POST   | `/api/rag/index/rebuild`                            | 全量重建 ES 关键词索引   |

### 检索与问答接口

| Method | Path              | Description                                    |
| ------ | ----------------- | ---------------------------------------------- |
| POST   | `/api/rag/search` | 根据查询内容检索相关知识片段，支持多种检索模式 |
| POST   | `/api/rag/ask`    | 基于知识库检索结果生成问答回复                 |

`/api/rag/search` 支持的典型请求字段包括：

```text
spaceId
query
retrievalMode
topK
candidateK
```

其中 `candidateK` 表示混合检索中每一路召回的深度。请求不再包含 `vectorWeight` / `keywordWeight` 字段（属破坏性变更：RRF 为等权融合，权重参数对其无效）。

`/api/rag/ask` 会返回生成结果以及匹配到的来源片段，典型字段包括：

```text
spaceId
question
answer
referenceCount
references
sessionId
userMessageId
assistantMessageId
```

其中，`references` 用于展示回答所依据的知识片段，便于检查回答是否来自知识库内容。

### RAG Evaluation 接口

| Method | Path                                       | Description                    |
| ------ | ------------------------------------------ | ------------------------------ |
| POST   | `/api/rag/eval/datasets`                   | 创建评测数据集                 |
| GET    | `/api/rag/eval/datasets`                   | 查询评测数据集列表             |
| GET    | `/api/rag/eval/datasets/{datasetId}`       | 查询指定评测数据集             |
| POST   | `/api/rag/eval/datasets/{datasetId}/cases` | 为指定评测集添加评测 case      |
| GET    | `/api/rag/eval/datasets/{datasetId}/cases` | 查询指定评测集下的 case 列表   |
| POST   | `/api/rag/eval/runs`                       | 启动一次评测运行               |
| GET    | `/api/rag/eval/runs/{runId}`               | 查询评测运行汇总结果           |
| GET    | `/api/rag/eval/runs/{runId}/results`       | 查询评测运行下每个 case 的结果 |
| POST   | `/api/rag/eval/runs/compare`               | 对多个评测 run 进行横向对比    |

## 快速开始

### 1. 启动依赖服务

使用 Docker Desktop 或本地环境启动以下服务：

```text
MySQL
Qdrant
Elasticsearch
TEI Rerank 服务（默认 http://localhost:8081）
```

MySQL 用于保存结构化业务数据、索引任务、问答历史和评测记录，Qdrant 用于保存文本片段对应的向量数据，Elasticsearch 用于保存关键词索引，TEI 提供重排模型的 `/rerank` 接口。

### 2. 配置环境变量

在 Windows PowerShell 中配置 API Key：

```powershell
$env:OPENAI_API_KEY="your_api_key_here"
```

如果使用兼容 OpenAI 接口格式的第三方服务，也可以配置 Base URL：

```powershell
$env:OPENAI_BASE_URL="your_base_url_here"
```

### 3. 配置 `application.yml`

示例配置如下：

```yaml
server:
  port: 8080

spring:
  application:
    name: rag-agent-knowledge-assistant

  task:
    scheduling:
      pool:
        size: 3

  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/rag_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: rag_user
    password: rag_password

  sql:
    init:
      mode: always
      schema-locations: classpath:db/schema.sql

  data:
    elasticsearch:
      uris: http://localhost:9200

  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
      chat:
        options:
          model: gpt-4o-mini
          temperature: 0.3
      embedding:
        options:
          model: text-embedding-3-small

    vectorstore:
      qdrant:
        host: localhost
        port: 6334
        collection-name: rag_kb_chunks
        use-tls: false
        initialize-schema: true

rag:
  chunk:
    strategy: FIXED_SIZE
    max-size: 500
    overlap-size: 80

  rerank:
    base-url: http://localhost:8081

  retrieval:
    rerank-candidate-top-m: 30
    vector-max-top-k: 50

  reconciliation:
    cron: 0 0 3 * * *

  task:
    worker-poll-ms: 2000
    worker-initial-delay-ms: 5000
    worker-batch-size: 10
    recover-poll-ms: 30000
    running-timeout-ms: 1800000
    max-retry-times: 5
    retry-base-delay-ms: 1000
    retry-max-delay-ms: 60000
```

检索与索引相关配置项说明：

| 配置项                                  | 默认值            | 说明                                                                   |
| --------------------------------------- | ----------------- | ---------------------------------------------------------------------- |
| `rag.retrieval.rerank-candidate-top-m`  | 30                | `HYBRID_RERANK` 送入重排模型的候选数上限，实际取 `max(该值, topK)`     |
| `rag.retrieval.vector-max-top-k`        | 50                | 向量检索 topK 上限，需覆盖 `candidateK` 的取值范围                     |
| `rag.rerank.base-url`                   | http://localhost:8081 | TEI 重排服务地址，调用其 `/rerank` 接口                            |
| `rag.reconciliation.cron`               | `0 0 3 * * *`     | 周期索引对账的执行时间，默认每天 03:00                                 |
| `rag.task.worker-poll-ms`               | 2000              | worker 轮询可执行任务（PENDING / 到期 RETRY_WAIT）的间隔               |
| `rag.task.worker-batch-size`            | 10                | worker 单批拉取的任务数                                                |
| `rag.task.running-timeout-ms`           | 1800000           | `RUNNING` 任务超时阈值，超时后回收重排（30 分钟）                      |
| `rag.task.max-retry-times`              | 5                 | 失败任务最大重试次数，超过后置 `FAILED`                                |
| `rag.task.retry-base-delay-ms`          | 1000              | 重试退避基数（base × 2^n）                                             |
| `rag.task.retry-max-delay-ms`           | 60000             | 重试退避上限（60s）                                                    |

> 注意：schema 脚本通过 `spring.sql.init.mode=always` 以 `CREATE TABLE IF NOT EXISTS` 方式执行，不会修改已存在的表结构。表结构发生变更后，需要重建开发库或手工执行 `ALTER`。

### 4. 启动后端服务

在 IntelliJ IDEA 中运行 Spring Boot 启动类：

```text
RagAgentKnowledgeAssistantApplication
```

默认服务地址为：

```text
http://localhost:8080
```

## 示例请求

### 创建知识空间

```http
POST http://localhost:8080/api/spaces
Content-Type: application/json

{
  "name": "Engineering Knowledge Base",
  "description": "用于保存工程项目文档和技术笔记的知识空间"
}
```

### 创建文档

```http
POST http://localhost:8080/api/documents
Content-Type: application/json

{
  "spaceId": 1,
  "title": "Service Layer Design",
  "content": "The service layer is used to organize business logic. Controllers should not directly call database mappers. In a RAG application, documents are stored first, then split into chunks, and later converted into embeddings for semantic retrieval."
}
```

### 手动触发索引构建任务

```http
POST http://localhost:8080/api/rag/documents/1/index
```

### 查询索引任务状态

```http
GET http://localhost:8080/api/rag/index-tasks/1
```

### 人工触发全量对账

```http
POST http://localhost:8080/api/rag/index/reconcile
```

### 向量检索知识片段

```http
POST http://localhost:8080/api/rag/search
Content-Type: application/json

{
  "spaceId": 1,
  "query": "What is the service layer used for?",
  "retrievalMode": "VECTOR_ONLY",
  "topK": 5
}
```

### 混合检索与重排序

```http
POST http://localhost:8080/api/rag/search
Content-Type: application/json

{
  "spaceId": 1,
  "query": "IndexTaskService 持久化索引任务",
  "retrievalMode": "HYBRID_RERANK",
  "topK": 5,
  "candidateK": 20
}
```

### 基于知识库问答

```http
POST http://localhost:8080/api/rag/ask
Content-Type: application/json

{
  "spaceId": 1,
  "question": "What is the service layer used for?",
  "retrievalMode": "HYBRID_RERANK",
  "topK": 5,
  "candidateK": 20
}
```

### 创建 RAG 评测数据集

```http
POST http://localhost:8080/api/rag/eval/datasets
Content-Type: application/json

{
  "spaceId": 1,
  "name": "RAG 检索增强评测集",
  "description": "用于比较 VECTOR_ONLY、KEYWORD_ONLY、HYBRID、HYBRID_RERANK 的检索效果"
}
```

### 添加评测 case

```http
POST http://localhost:8080/api/rag/eval/datasets/1/cases
Content-Type: application/json

{
  "question": "持久化索引任务的作用是什么？",
  "expectedAnswer": "持久化索引任务用于记录文档索引构建与清理的过程，包括任务类型（BUILD_INDEX / DELETE_INDEX / REPAIR_INDEX）、任务状态、重试次数、成功/失败/跳过 chunk 数量和时间信息。",
  "expectedChunkIds": [1, 2],
  "expectedKeywords": ["BUILD_INDEX", "DELETE_INDEX", "REPAIR_INDEX", "任务状态", "重试"],
  "difficulty": "easy"
}
```

### 启动一次评测运行

```http
POST http://localhost:8080/api/rag/eval/runs
Content-Type: application/json

{
  "datasetId": 1,
  "runName": "hybrid-rerank-top5",
  "retrievalMode": "HYBRID_RERANK",
  "topK": 5,
  "candidateK": 20,
  "enableAnswerGeneration": true
}
```

### 对比多个评测 run

```http
POST http://localhost:8080/api/rag/eval/runs/compare
Content-Type: application/json

{
  "runIds": [1, 2, 3, 4]
}
```

## 设计亮点

### 完整的 RAG 后端链路

项目覆盖了从文档写入、文本切分、索引任务调度、Embedding 向量化、向量与关键词索引写入、检索召回、问答生成、引用返回到问答历史保存的主要流程，能够体现一个知识库问答系统的核心后端实现方式。

### 结构化数据与向量数据分离

系统使用 MySQL 保存知识空间、文档、文本片段、索引任务、问答历史和评测结果，使用 Qdrant 保存向量数据，使用 Elasticsearch 保存关键词索引。三者职责分离，使业务数据管理和语义/关键词检索能力可以独立维护和扩展。

### 持久化异步索引任务机制

文档的索引工作（Qdrant 向量 + Elasticsearch 关键词双写）统一走持久化任务队列 `kb_index_task`，文档操作不再同步执行索引工作：

- **任务类型**：`BUILD_INDEX`（新文档建索引）、`DELETE_INDEX`（删除文档索引）、`REPAIR_INDEX`（对账补建）。更新 = DELETE_INDEX(旧文档) + BUILD_INDEX(新文档)。
- **状态机**：`PENDING → RUNNING → RETRY_WAIT / SUCCESS / FAILED`（0 / 1 / 2 / 3 / 4）。
- **触发**：文档创建/删除/更新在事务提交后（afterCommit）登记任务；周期对账发现 `ACTIVE` 文档索引缺失时自动登记 `REPAIR_INDEX`。
- **调度**：worker（`IndexTaskWorker`）按 `@Scheduled` 轮询拉取 PENDING / 到期 RETRY_WAIT 任务，条件更新抢占后经 `@Async` 线程池执行，避免重复调度。
- **可靠性**：任务落库，应用重启后自动续跑；失败按指数退避自动重试（base × 2^n，上限 60s，默认 5 次后置 `FAILED`）；`RUNNING` 超时（默认 30min）自动回收重排；提供人工重试 `FAILED` 任务接口。
- **去重**：同一文档的写索引类任务互斥（`BUILD_INDEX` / `REPAIR_INDEX` 共用 `BUILD` 分组，`DELETE_INDEX` 独立成组），由 MySQL 生成列 `dedup_group`、`active_flag` 与唯一索引 `uk_task_active_dedup` 在数据库层保证活跃任务每组至多一条。
- **失效保护**：索引构建期间文档被删除或更新时，收尾阶段以锁定读确认文档状态，若已失效则清理本次写入的索引并中止任务，避免把已删除文档重新置为 `ACTIVE`。
- **对账兜底**：周期对账清理 Qdrant / Elasticsearch 孤儿索引，并为索引缺失的 `ACTIVE` 文档登记补建任务。

### Hybrid Search + Rerank 检索增强

项目不仅支持基础向量检索，还支持基于 Elasticsearch + smartcn 的关键词检索、等权 RRF 混合检索和 TEI 模型重排序。该设计能够缓解单纯向量检索对类名、方法名、接口路径和技术术语不稳定的问题，并通过多类分数字段提升检索结果的可解释性。混合检索中每一路的召回深度由 `candidateK` 控制，可独立作为评测变量。

### 可追踪的问答结果

RAG 问答接口不仅返回最终回答，还返回匹配到的来源片段，并将问答会话、消息和引用记录保存到数据库。这样可以检查回答是否基于知识库内容生成，提高系统的可解释性和可调试性。

### RAG Evaluation 评测能力

项目新增评测数据集、评测 case、评测 run 和 case result 等结构，使系统可以对不同检索策略进行量化评估。通过 Recall@K、Hit@K、MRR、回答关键词命中率、引用精确率、引用召回率、grounded 比例和平均延迟等指标，可以比较不同 RAG 策略的实际效果，并通过引用编号的解析判定引用是否被编造。

### 清晰的后端分层结构

项目按照 Controller、Service、Mapper、Entity、Request、Response 等层次组织代码，使接口处理、业务逻辑、数据库访问和数据传输对象保持清晰分离，便于后续维护和功能扩展。

## 后续规划

后续可能继续扩展以下能力：

- 增加更多的知识库数据和评测 case
- 文件上传与文档解析功能
- 更灵活的文本切分策略
- 相似度阈值配置与检索质量优化
- LLM-as-a-Judge 回答质量评估
- 更细粒度的 prompt 版本管理与对比
- Redis 缓存高频问题或检索结果
- 以消息队列替代当前的任务表轮询调度
- 用户登录与知识空间权限控制
- 前端管理页面
- Docker Compose 一键部署

## License

本项目暂未指定开源许可证。
