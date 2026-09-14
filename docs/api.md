# API 接口

本文列出全部对外接口与示例请求。参数语义见[检索增强](retrieval.md)，字段结构见[数据模型](data-model.md)。
其他文档：[架构](architecture.md) · [数据模型](data-model.md) · [配置](configuration.md) · [检索](retrieval.md) · [评测](evaluation.md) · [返回根 README](../README.md)

所有接口返回统一的 `ApiResponse` 结构，异常由全局异常处理器转换为一致的错误响应。应用启动后可通过 Swagger UI（`/swagger-ui.html`）查看在线接口文档。

## 健康检查接口

| Method | Path               | Description              |
| ------ | ------------------ | ------------------------ |
| GET    | `/api/health/ping` | 检查后端服务是否正常运行 |

## 知识空间接口

| Method | Path               | Description          |
| ------ | ------------------ | -------------------- |
| POST   | `/api/spaces`      | 创建新的知识空间     |
| GET    | `/api/spaces`      | 获取知识空间列表     |
| GET    | `/api/spaces/{id}` | 获取指定知识空间详情 |
| PUT    | `/api/spaces/{id}` | 更新指定知识空间信息 |
| DELETE | `/api/spaces/{id}` | 删除指定知识空间     |

## 文档接口

| Method | Path                       | Description                        |
| ------ | -------------------------- | ---------------------------------- |
| POST   | `/api/documents`           | 创建新的知识文档                   |
| GET    | `/api/documents`           | 获取知识文档列表，可按知识空间筛选 |
| GET    | `/api/documents/{id}`      | 获取指定知识文档详情               |
| PUT    | `/api/documents/{id}`      | 更新指定知识文档信息               |
| DELETE | `/api/documents/{id}`      | 删除指定知识文档                   |
| GET    | `/api/documents/{id}/chunks` | 获取指定文档切分后的 chunk 列表  |

## 索引任务接口

| Method | Path                                          | Description                                      |
| ------ | --------------------------------------------- | ------------------------------------------------ |
| POST   | `/api/rag/documents/{documentId}/index`       | 手动触发 BUILD_INDEX（登记任务，worker 异步执行） |
| GET    | `/api/rag/index-tasks/{taskId}`               | 根据任务 ID 查询索引任务状态                     |
| GET    | `/api/rag/documents/{documentId}/index-tasks` | 查询指定文档的索引任务记录                       |
| POST   | `/api/rag/index-tasks/{taskId}/retry`         | 人工重试 FAILED 任务                             |

## 索引维护接口

| Method | Path                                                | Description              |
| ------ | --------------------------------------------------- | ------------------------ |
| POST   | `/api/rag/index/reconcile`                          | 人工触发全量对账         |
| POST   | `/api/rag/index/documents/{documentId}/reconcile`   | 单文档对账               |
| POST   | `/api/rag/index/rebuild`                            | 全量重建 ES 关键词索引   |

## 检索与问答接口

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

## 问答历史接口

| Method | Path                                                  | Description              |
| ------ | ----------------------------------------------------- | ------------------------ |
| GET    | `/api/qa/history/spaces/{spaceId}/sessions`           | 按知识空间查询会话列表   |
| GET    | `/api/qa/history/sessions/{sessionId}/messages`       | 查询会话下的消息列表     |
| GET    | `/api/qa/history/sessions/{sessionId}`                | 查询完整会话历史         |
| GET    | `/api/qa/history/messages/{messageId}/references`     | 查询回答消息的引用记录   |

## RAG Evaluation 接口

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

## 示例请求

以下示例基于默认服务地址 `http://localhost:8080`。完整的可执行脚本见 [`evaluation/eval-http/`](../evaluation/eval-http/) 与 [`src/test/http/`](../src/test/http/)。

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
