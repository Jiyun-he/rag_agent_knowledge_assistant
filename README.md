# RAG Agent Knowledge Assistant

`rag_agent_knowledge_assistant` 是一个基于 Spring Boot 构建的 RAG（Retrieval-Augmented Generation，检索增强生成）知识库问答后端系统。项目围绕知识空间、文档管理、文本切分、Embedding 向量化、Qdrant 向量检索、异步向量化任务追踪和基于知识片段的问答接口，构建了一条较完整的 RAG 应用后端链路。

本项目面向真实 AI 应用开发场景，重点实现结构化数据管理、向量数据库接入、语义检索、异步任务管理、问答结果来源追踪等工程化能力。系统可以作为知识库问答、企业内部文档助手、项目资料检索助手等应用的后端基础。

## 功能特性

- 知识空间管理
- 文档创建、查询和更新
- 文档内容自动切分为文本片段
- MySQL 存储知识空间、文档、chunk 和任务状态
- OpenAI-compatible Embedding API 接入
- Qdrant 向量数据库接入
- 文档同步向量化与异步向量化
- 向量化任务状态追踪
- 基于语义相似度的知识片段检索
- 基于检索结果的 RAG 问答接口
- 问答结果返回来源片段，便于追踪回答依据
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
        +--------------------+
        |                    |
        v                    v
MySQL / MyBatis-Plus     Embedding API
        |                    |
        v                    v
Structured Metadata      Qdrant Vector Store
```

主包路径为：

```text
com.nuaa.ragagent
```

主要包结构如下：

| 包名                       | 作用                         |
| -------------------------- | ---------------------------- |
| `controller`               | 对外提供 REST API            |
| `service` / `service.impl` | 实现核心业务逻辑             |
| `mapper`                   | 通过 MyBatis-Plus 访问 MySQL |
| `entity`                   | 映射数据库表结构             |
| `request`                  | 封装接口请求参数             |
| `response`                 | 封装接口响应结果             |
| `common`                   | 提供统一响应结构             |
| `exception`                | 处理业务异常和全局异常       |
| `util`                     | 提供文本切分等工具逻辑       |
| `config`                   | 提供异步线程池等配置         |

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
根据用户问题进行语义检索
        ↓
获取相关知识片段
        ↓
基于检索上下文生成回答
        ↓
返回回答内容和来源片段
```

通过该流程，系统能够同时保留文档的结构化管理能力和面向语义检索的向量索引能力。

## 数据模型

当前系统使用以下核心数据表：

```text
kb_space
kb_document
kb_chunk
kb_embedding_task
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

## API 概览

### 知识空间接口

| Method | Path | Description |
|---|---|---|
| POST | `/api/spaces` | 创建新的知识空间 |
| GET | `/api/spaces` | 获取知识空间列表 |
| GET | `/api/spaces/{id}` | 获取指定知识空间详情 |
| PUT | `/api/spaces/{id}` | 更新指定知识空间信息 |
| DELETE | `/api/spaces/{id}` | 删除指定知识空间 |

### 文档接口

| Method | Path | Description |
|---|---|---|
| POST | `/api/documents` | 创建新的知识文档 |
| GET | `/api/documents` | 获取知识文档列表，可按知识空间筛选 |
| GET | `/api/documents/{id}` | 获取指定知识文档详情 |
| PUT | `/api/documents/{id}` | 更新指定知识文档信息 |
| DELETE | `/api/documents/{id}` | 删除指定知识文档 |

### 文档向量化接口

| Method | Path | Description |
|---|---|---|
| POST | `/api/rag/documents/{documentId}/vectorize-async` | 创建指定文档的异步向量化任务 |
| GET | `/api/rag/embedding-tasks/{taskId}` | 根据任务 ID 查询向量化任务状态 |
| GET | `/api/rag/documents/{documentId}/embedding-tasks` | 查询指定文档的向量化任务记录 |

### 检索与问答接口

| Method | Path | Description |
|---|---|---|
| POST | `/api/rag/search` | 根据查询内容检索相关知识片段 |
| POST | `/api/rag/ask` | 基于知识库检索结果生成问答回复 |

问答接口会返回生成结果以及匹配到的来源片段，典型字段包括：

```text
answer
hasContext
noAnswerReason
matchedCount
matchedChunks
```

其中，`matchedChunks` 用于展示回答所依据的知识片段，便于检查回答是否来自知识库内容。

## 快速开始

### 1. 启动依赖服务

使用 Docker Desktop 或本地环境启动以下服务：

```text
MySQL
Qdrant
```

MySQL 用于保存结构化业务数据，Qdrant 用于保存文本片段对应的向量数据。

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

### 检索相关知识片段

```http
POST http://localhost:8080/api/rag/search
Content-Type: application/json

{
  "spaceId": 1,
  "query": "What is the service layer used for?",
  "topK": 5
}
```

### 基于知识库问答

```http
POST http://localhost:8080/api/rag/ask
Content-Type: application/json

{
  "spaceId": 1,
  "question": "What is the service layer used for?",
  "topK": 5
}
```

## 设计亮点

### 完整的 RAG 后端链路

项目覆盖了从文档写入、文本切分、Embedding 向量化、向量存储、语义检索到问答返回的主要流程，能够体现一个知识库问答系统的核心后端实现方式。

### 结构化数据与向量数据分离

系统使用 MySQL 保存知识空间、文档、文本片段和任务记录，使用 Qdrant 保存向量数据。二者职责分离，使业务数据管理和语义检索能力可以独立维护和扩展。

### 异步向量化任务机制

文档向量化可能涉及多个 chunk 的 Embedding 调用，耗时相对较长。系统通过 Spring `@Async` 和任务表实现异步处理，使接口可以先返回任务 ID，再由用户查询任务执行状态。

### 可追踪的问答结果

RAG 问答接口不仅返回最终回答，还返回匹配到的来源片段。这样可以检查回答是否基于知识库内容生成，提高系统的可解释性和可调试性。

### 清晰的后端分层结构

项目按照 Controller、Service、Mapper、Entity、Request、Response 等层次组织代码，使接口处理、业务逻辑、数据库访问和数据传输对象保持清晰分离，便于后续维护和功能扩展。

## 后续规划

后续可以继续扩展以下能力：

- 文件上传与文档解析
- 更灵活的文本切分策略
- 相似度阈值配置与检索质量优化
- Redis 缓存高频问题或检索结果
- RabbitMQ 任务队列替代简单异步执行
- 用户登录与知识空间权限控制
- 问答历史记录
- 前端管理页面
- Docker Compose 一键部署
- 更系统的 RAG 评估指标

## License

本项目暂未指定开源许可证。