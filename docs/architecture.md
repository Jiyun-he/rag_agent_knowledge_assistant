# 系统架构与核心流程

本文说明系统的分层结构、包组织、RAG 主链路，以及若干关键设计取舍。
其他文档：[数据模型](data-model.md) · [API](api.md) · [配置](configuration.md) · [检索](retrieval.md) · [评测](evaluation.md) · [返回根 README](../README.md)

## 分层结构

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

字段与状态定义的完整说明见[数据模型](data-model.md#kb_index_task)。

### Hybrid Search + Rerank 检索增强

项目不仅支持基础向量检索，还支持基于 Elasticsearch + smartcn 的关键词检索、等权 RRF 混合检索和 TEI 模型重排序。该设计能够缓解单纯向量检索对类名、方法名、接口路径和技术术语不稳定的问题，并通过多类分数字段提升检索结果的可解释性。混合检索中每一路的召回深度由 `candidateK` 控制，可独立作为评测变量。

详见[检索增强](retrieval.md)。

### 可追踪的问答结果

RAG 问答接口不仅返回最终回答，还返回匹配到的来源片段，并将问答会话、消息和引用记录保存到数据库。这样可以检查回答是否基于知识库内容生成，提高系统的可解释性和可调试性。

### RAG Evaluation 评测能力

项目新增评测数据集、评测 case、评测 run 和 case result 等结构，使系统可以对不同检索策略进行量化评估。通过 Recall@K、Hit@K、MRR、回答关键词命中率、引用精确率、引用召回率、grounded 比例和平均延迟等指标，可以比较不同 RAG 策略的实际效果，并通过引用编号的解析判定引用是否被编造。

### 清晰的后端分层结构

项目按照 Controller、Service、Mapper、Entity、Request、Response 等层次组织代码，使接口处理、业务逻辑、数据库访问和数据传输对象保持清晰分离，便于后续维护和功能扩展。
