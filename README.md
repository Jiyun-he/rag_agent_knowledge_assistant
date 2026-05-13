# RAG Agent Knowledge Assistant

`rag_agent_knowledge_assistant` 是一个基于 Spring Boot 构建的 RAG（Retrieval-Augmented Generation，检索增强生成）知识库问答后端系统。项目围绕知识空间、文档管理、文本切分、Embedding 向量化、Qdrant 向量检索、Hybrid Search + Rerank、RAG 问答、问答历史保存、引用追踪和 RAG 评测，构建了一条较完整的知识库问答系统后端链路。

本项目面向真实 AI 应用开发场景，重点实现结构化数据管理、向量数据库接入、语义检索、关键词召回、混合检索、检索重排序、异步任务管理、问答结果来源追踪和 RAG 策略评测等工程化能力。系统可以作为知识库问答、企业内部文档助手、项目资料检索助手、RAG 检索策略实验平台等应用的后端基础。

## 功能特性

- 知识空间管理
- 文档创建、查询、更新和删除
- 文档内容自动切分为文本片段
- MySQL 存储知识空间、文档、chunk、任务状态、问答历史和评测记录
- OpenAI-compatible Embedding API 接入
- OpenAI-compatible Chat API 接入
- Qdrant 向量数据库接入
- 文档同步向量化与异步向量化
- 向量化任务状态追踪
- 基于 Qdrant 的向量语义检索
- 基于 MySQL 的关键词检索
- Hybrid Search：向量召回与关键词召回融合
- Rule-based Rerank：对混合召回结果进行重排序
- 支持 `VECTOR_ONLY`、`KEYWORD_ONLY`、`HYBRID`、`HYBRID_RERANK` 多种检索模式
- 检索结果返回向量分数、关键词分数、融合分数、重排序分数和最终排序分数
- 基于检索结果的 RAG 问答接口
- 问答结果返回来源片段，便于追踪回答依据
- 问答历史保存与引用记录
- RAG Evaluation 评测数据集、评测 case、评测 run 和 case 结果保存
- 支持 Hit@K、Recall@K、MRR、Answer Keyword Hit、Citation Correct Rate、Average Latency 等评测指标
- 支持多个评测 run 的横向对比
- 统一 API 返回格式
- 全局异常处理

## 技术栈

| 模块           | 技术                                  |
| -------------- | ------------------------------------- |
| 后端框架       | Spring Boot                           |
| ORM 框架       | MyBatis-Plus                          |
| 结构化数据库   | MySQL                                 |
| 向量数据库     | Qdrant                                |
| Embedding 服务 | OpenAI-compatible Embedding API       |
| Chat 服务      | OpenAI-compatible Chat API            |
| 异步处理       | Spring `@Async`                       |
| 接口测试       | IntelliJ IDEA HTTP Client / REST 工具 |
| 容器管理       | Docker Desktop                        |
| 构建工具       | Maven                                 |

## 系统架构

项目采用典型的 Spring Boot 分层架构，将接口层、业务层、数据访问层和外部服务调用进行拆分。

```text
Client / API Tester
        |
        v
Controller Layer
        |
        v
Service Layer
        |
        +--------------------+--------------------+--------------------+
        |                    |                    |                    |
        v                    v                    v                    v
MySQL / MyBatis-Plus     Embedding API       Chat API          Qdrant Vector Store
        |
        v
Structured Metadata / QA History / Evaluation Records
```

主包路径为：

```text
com.nuaa.ragagent
```

主要包结构如下：

| 包名                       | 作用                                    |
| -------------------------- | --------------------------------------- |
| `controller`               | 对外提供 REST API                       |
| `service` / `service.impl` | 实现核心业务逻辑                        |
| `mapper`                   | 通过 MyBatis-Plus 访问 MySQL            |
| `entity`                   | 映射数据库表结构                        |
| `request`                  | 封装接口请求参数                        |
| `response`                 | 封装接口响应结果                        |
| `common`                   | 提供统一响应结构                        |
| `exception`                | 处理业务异常和全局异常                  |
| `util`                     | 提供文本切分、RAG prompt 构造等工具逻辑 |
| `config`                   | 提供异步线程池、AI 客户端等配置         |
| `enums`                    | 提供检索模式等枚举定义                  |

## 核心流程

系统的主要 RAG 工作流程如下：

```text
创建知识空间
        ↓
创建文档
        ↓
文档内容切分为 chunks
        ↓
调用 Embedding API 生成向量
        ↓
将向量写入 Qdrant
        ↓
更新 MySQL 中的 chunk 向量化状态
        ↓
根据用户问题进行检索
        ↓
按检索模式执行向量检索、关键词检索、混合检索或重排序
        ↓
获取相关知识片段
        ↓
基于检索上下文生成回答
        ↓
返回回答内容和来源片段
        ↓
保存问答历史与引用记录
```

通过该流程，系统能够同时保留文档的结构化管理能力、面向语义检索的向量索引能力、面向精确术语的关键词召回能力，以及面向回答可追溯性的引用记录能力。

## Hybrid Search + Rerank 检索增强

项目在基础向量检索能力之上，进一步扩展了多种检索模式，用于提升 RAG 场景下的召回质量和可解释性。

当前支持的检索模式包括：

| 检索模式        | 说明                                                         |
| --------------- | ------------------------------------------------------------ |
| `VECTOR_ONLY`   | 仅使用向量检索，从 Qdrant 中召回相似 chunk。                 |
| `KEYWORD_ONLY`  | 仅使用关键词检索，适合类名、方法名、接口路径、技术术语等精确匹配场景。 |
| `HYBRID`        | 同时执行向量召回和关键词召回，并对两类分数进行融合。         |
| `HYBRID_RERANK` | 在混合召回的基础上进行规则重排序，综合考虑语义相似度、关键词命中和技术符号匹配情况。 |

检索请求支持的主要参数包括：

```text
retrievalMode
query
topK
candidateK
vectorWeight
keywordWeight
```

检索结果会返回以下分数信息：

```text
vectorScore
keywordScore
hybridScore
rerankScore
finalScore
retrievalSource
```

其中，`retrievalSource` 用于表示结果来源，例如 `VECTOR`、`KEYWORD` 或 `BOTH`。这些字段便于分析不同 chunk 的召回来源、排序依据和检索策略差异。

## RAG Evaluation 评测模块

项目新增 RAG Evaluation 模块，用于评估不同 RAG 检索策略在同一评测数据集上的表现。

该模块支持：

- 创建评测数据集；
- 添加评测问题；
- 为每个问题配置期望召回的 chunkId 和期望关键词；
- 启动一次评测运行；
- 指定检索模式、topK、candidateK、向量权重和关键词权重；
- 保存每个 case 的检索结果和回答结果；
- 汇总整次评测运行的指标；
- 对多个评测 run 进行横向比较。

当前支持的评测指标包括：

| 指标                    | 说明                                      |
| ----------------------- | ----------------------------------------- |
| `Hit@K`                 | 前 K 个结果中是否命中至少一个期望 chunk。 |
| `Recall@K`              | 前 K 个结果召回了多少期望 chunk。         |
| `MRR`                   | 第一个正确 chunk 的倒数排名。             |
| `Answer Keyword Hit`    | 回答中命中期望关键词的比例。              |
| `Citation Correct Rate` | 引用结果是否命中期望 chunk。              |
| `Average Latency`       | 平均检索或问答耗时。                      |

该模块主要用于比较 `VECTOR_ONLY`、`KEYWORD_ONLY`、`HYBRID`、`HYBRID_RERANK` 等不同检索策略的效果，为后续 RAG 参数调优和检索链路优化提供量化依据。

> 当前 README 仅说明评测模块能力，不写入具体策略优劣结论。待后续准备真实知识库数据和评测 case 后，再补充真实评测结果表格。

## 数据模型

当前系统使用以下核心数据表：

```text
kb_space
kb_document
kb_chunk
kb_embedding_task
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

### `kb_embedding_task`

用于保存异步向量化任务记录，使文档向量化过程可以被提交、执行和查询。

任务状态包括：

```text
0 = pending
1 = running
2 = success
3 = failed
4 = partial_success
```

通过任务表，系统可以避免在接口请求中长时间阻塞，同时也便于查看向量化是否成功、失败或部分成功。

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

用于保存一次评测运行的配置和汇总结果，包括检索模式、topK、candidateK、权重参数、总 case 数、成功数量、失败数量以及整体指标。

### `eval_case_result`

用于保存每个评测 case 的执行结果，包括实际召回 chunkId、参考 chunkId、Recall@K、Hit@K、MRR、关键词命中率、引用正确性和耗时等信息。

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

| Method | Path                  | Description                        |
| ------ | --------------------- | ---------------------------------- |
| POST   | `/api/documents`      | 创建新的知识文档                   |
| GET    | `/api/documents`      | 获取知识文档列表，可按知识空间筛选 |
| GET    | `/api/documents/{id}` | 获取指定知识文档详情               |
| PUT    | `/api/documents/{id}` | 更新指定知识文档信息               |
| DELETE | `/api/documents/{id}` | 删除指定知识文档                   |

### 文档向量化接口

| Method | Path                                              | Description                    |
| ------ | ------------------------------------------------- | ------------------------------ |
| POST   | `/api/rag/documents/{documentId}/vectorize-async` | 创建指定文档的异步向量化任务   |
| GET    | `/api/rag/embedding-tasks/{taskId}`               | 根据任务 ID 查询向量化任务状态 |
| GET    | `/api/rag/documents/{documentId}/embedding-tasks` | 查询指定文档的向量化任务记录   |

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
vectorWeight
keywordWeight
```

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
```

MySQL 用于保存结构化业务数据、问答历史和评测记录，Qdrant 用于保存文本片段对应的向量数据。

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

  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/rag_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: rag_user
    password: rag_password

  sql:
    init:
      mode: never

  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
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
```

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

### 提交异步向量化任务

```http
POST http://localhost:8080/api/rag/documents/1/vectorize-async
```

### 查询向量化任务状态

```http
GET http://localhost:8080/api/rag/embedding-tasks/1
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
  "query": "EmbeddingTaskService 异步向量化任务",
  "retrievalMode": "HYBRID_RERANK",
  "topK": 5,
  "candidateK": 20,
  "vectorWeight": 0.6,
  "keywordWeight": 0.4
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
  "candidateK": 20,
  "vectorWeight": 0.6,
  "keywordWeight": 0.4
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
  "question": "异步向量化任务的作用是什么？",
  "expectedAnswer": "异步向量化任务用于记录文档向量化过程中的任务状态、成功数量、失败数量、跳过数量、开始时间、结束时间和错误信息。",
  "expectedChunkIds": [1, 2],
  "expectedKeywords": ["异步向量化", "任务状态", "成功", "失败", "跳过"],
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
  "vectorWeight": 0.6,
  "keywordWeight": 0.4,
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

项目覆盖了从文档写入、文本切分、Embedding 向量化、向量存储、检索召回、问答生成、引用返回到问答历史保存的主要流程，能够体现一个知识库问答系统的核心后端实现方式。

### 结构化数据与向量数据分离

系统使用 MySQL 保存知识空间、文档、文本片段、任务记录、问答历史和评测结果，使用 Qdrant 保存向量数据。二者职责分离，使业务数据管理和语义检索能力可以独立维护和扩展。

### 异步向量化任务机制

文档向量化可能涉及多个 chunk 的 Embedding 调用，耗时相对较长。系统通过 Spring `@Async` 和任务表实现异步处理，使接口可以先返回任务 ID，再由用户查询任务执行状态。

### Hybrid Search + Rerank 检索增强

项目不仅支持基础向量检索，还支持关键词检索、混合检索和规则重排序。该设计能够缓解单纯向量检索对类名、方法名、接口路径和技术术语不稳定的问题，并通过多类分数字段提升检索结果的可解释性。

### 可追踪的问答结果

RAG 问答接口不仅返回最终回答，还返回匹配到的来源片段，并将问答会话、消息和引用记录保存到数据库。这样可以检查回答是否基于知识库内容生成，提高系统的可解释性和可调试性。

### RAG Evaluation 评测能力

项目新增评测数据集、评测 case、评测 run 和 case result 等结构，使系统可以对不同检索策略进行量化评估。通过 Hit@K、Recall@K、MRR、关键词命中率、引用正确率和平均延迟等指标，可以比较不同 RAG 策略的实际效果。

### 清晰的后端分层结构

项目按照 Controller、Service、Mapper、Entity、Request、Response 等层次组织代码，使接口处理、业务逻辑、数据库访问和数据传输对象保持清晰分离，便于后续维护和功能扩展。

## 评测结果说明

当前项目已经具备 RAG 评测和多 run 对比能力，但暂不在 README 中写入具体性能结论。原因是当前知识库数据量和评测 case 还不足以支撑稳定、可信的策略优劣判断。

后续准备真实知识库数据后，将补充如下格式的评测结果表：

| Retrieval Mode |  Hit@5 | Recall@5 |    MRR | Avg Latency |
| -------------- | -----: | -------: | -----: | ----------: |
| VECTOR_ONLY    | 待补充 |   待补充 | 待补充 |      待补充 |
| KEYWORD_ONLY   | 待补充 |   待补充 | 待补充 |      待补充 |
| HYBRID         | 待补充 |   待补充 | 待补充 |      待补充 |
| HYBRID_RERANK  | 待补充 |   待补充 | 待补充 |      待补充 |

建议后续准备 3 到 5 篇真实项目文档，构建 20 到 30 个评测问题，并为每个问题标注 1 到 3 个期望召回 chunkId，再分别运行四种检索策略进行对比。

## 后续规划

后续可能继续扩展以下能力：

- 准备真实知识库数据和正式评测 case
- 将真实评测结果写入 README
- 文件上传与文档解析
- 更灵活的文本切分策略
- 相似度阈值配置与检索质量优化
- LLM-as-a-Judge 回答质量评估
- 更细粒度的 prompt 版本管理与对比
- Redis 缓存高频问题或检索结果
- RabbitMQ 任务队列替代简单异步执行
- 用户登录与知识空间权限控制
- 前端管理页面
- Docker Compose 一键部署

## License

本项目暂未指定开源许可证。