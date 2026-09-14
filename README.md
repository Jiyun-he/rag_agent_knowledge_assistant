# RAG Agent Knowledge Assistant

`rag_agent_knowledge_assistant` 是一个基于 Spring Boot 构建的 RAG（Retrieval-Augmented Generation，检索增强生成）知识库问答后端系统。项目围绕知识空间、文档管理、文本切分、Embedding 向量化、Qdrant 向量检索、Elasticsearch 关键词检索、Hybrid Search + Rerank、RAG 问答、问答历史保存、引用追踪和 RAG 评测，构建了一条较完整的知识库问答系统后端链路。

系统覆盖结构化数据管理、向量数据库接入、语义检索、关键词召回、混合检索、检索重排序、持久化索引任务调度、问答结果来源追踪和 RAG 策略评测等工程能力，可作为知识库问答、企业内部文档助手、项目资料检索助手或 RAG 检索策略实验平台的后端基础。

## 功能特性

**知识库与文档**

- 知识空间管理
- 文档创建、查询、更新和删除
- 文档内容自动切分为文本片段

**索引与一致性**

- MySQL 存储知识空间、文档、chunk、索引任务、问答历史和评测记录
- 文档状态四态管理（INVALID / ACTIVE / INDEXING / FAILED）
- Qdrant 向量数据库接入
- Elasticsearch 关键词索引（smartcn 中文分词）
- 持久化索引任务：文档创建/更新/删除在事务提交后（afterCommit）登记任务，由 worker 调度执行
- 索引任务失败指数退避重试、RUNNING 超时回收、人工重试
- 周期索引对账：清理 Qdrant / Elasticsearch 孤儿索引，并为索引缺失的 ACTIVE 文档补建

**检索**

- 基于 Qdrant 的向量语义检索
- 基于 Elasticsearch 的关键词检索
- Hybrid Search：向量召回与关键词召回按等权 RRF 融合
- TEI Rerank：对混合召回结果调用重排模型重排序
- 支持 `VECTOR_ONLY`、`KEYWORD_ONLY`、`HYBRID`、`HYBRID_RERANK` 多种检索模式
- 检索结果返回向量分数、关键词分数、融合分数、重排序分数和最终排序分数

**问答与历史**

- OpenAI-compatible Embedding API 接入
- OpenAI-compatible Chat API 接入
- 基于检索结果的 RAG 问答接口
- 问答结果返回来源片段，便于追踪回答依据
- 问答历史保存与引用记录

**评测**

- RAG Evaluation 评测数据集、评测 case、评测 run 和 case 结果保存
- 支持 Recall@K、Hit@K、MRR、Answer Keyword Hit、引用精确率、引用召回率、grounded、Average Latency 等评测指标
- 支持多个评测 run 的横向对比

**通用**

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
| 接口文档       | SpringDoc OpenAPI（Swagger UI）                         |
| 容器化         | Docker / Docker Compose                                 |
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

主包路径为 `com.nuaa.ragagent`。完整的包结构、RAG 主链路流程图与设计取舍见 [docs/architecture.md](docs/architecture.md)。

## 快速开始

### 1. 启动依赖服务

`docker-compose.yml` 提供了 MySQL、Qdrant、Elasticsearch、TEI Rerank 四个必需服务：

```bash
docker compose up -d
```

TEI Rerank 首次启动需要下载 `BAAI/bge-reranker-v2-m3` 模型，耗时较长。compose 文件中另有 `redis` 与 `rabbitmq` 两个服务，当前**暂未使用**，为后续规划预留。各服务端口与分工见 [docs/configuration.md](docs/configuration.md#依赖服务)。

### 2. 配置环境变量

应用通过 `spring-dotenv` 自动加载根目录的 `.env` 文件：

```dotenv
OPENAI_API_KEY=your_api_key_here
OPENAI_BASE_URL=https://api.openai.com
```

`OPENAI_BASE_URL` 可指向官方 OpenAI，也可指向兼容 OpenAI 接口格式的第三方服务或本地推理服务。`.env` 已在 `.gitignore` 中排除。

### 3. 启动后端服务

```bash
# 使用 Maven Wrapper
./mvnw spring-boot:run
```

启动类为 `RagAgentKnowledgeAssistantApplication`。默认服务地址为 `http://localhost:8080`，Swagger UI 位于 `http://localhost:8080/swagger-ui.html`。

完整的 `application.yml` 与全部配置项说明见 [docs/configuration.md](docs/configuration.md)。

## 测试

| 类型 | 位置 | 说明 |
| --- | --- | --- |
| 单元测试 | `src/test/java/.../util/`、`.../service/impl/` | 基于 JUnit + Mockito，不启动 Spring 上下文，**不需要外部服务** |
| 端到端测试 | `src/test/java/.../e2e/` | `@SpringBootTest` + `TestRestTemplate`，Chat / Embedding 调用由 fake bean 替代（见 `e2e/TestAiConfiguration.java` 与 `src/test/resources/application-test.yml`），但仍需真实 MySQL / Qdrant / Elasticsearch |
| 探索性测试 | `src/test/java/.../explore/` | 输出切分结果、AST 结构等中间产物供人工查看，**非回归测试** |
| 接口脚本 | `src/test/http/` | 手工执行的 `.http` 文件，说明见 [src/test/http/README.md](src/test/http/README.md) |

```bash
./mvnw test
```

## 文档导航

| 文档 | 内容 |
| --- | --- |
| [docs/architecture.md](docs/architecture.md) | 分层结构与包组织、RAG 主链路、设计亮点 |
| [docs/data-model.md](docs/data-model.md) | 核心数据表、文档四态、索引任务类型/状态/去重/可靠性 |
| [docs/api.md](docs/api.md) | 全部接口清单与示例请求 |
| [docs/configuration.md](docs/configuration.md) | 依赖服务、环境变量、`application.yml` 完整配置项 |
| [docs/retrieval.md](docs/retrieval.md) | 四种检索模式、`candidateK` 语义、RRF 融合、分数字段 |
| [docs/evaluation.md](docs/evaluation.md) | 评测指标定义、引用指标实现、评测流程与结果摘要 |
| [evaluation/README.md](evaluation/README.md) | 评测目录导航：演示语料、可执行脚本、标注文件 |
| [evaluation/mybatis-eval/RESULTS.md](evaluation/mybatis-eval/RESULTS.md) | MyBatis 语料上的完整实验结果与逐项解读 |
| [dataset/README.md](dataset/README.md) | 两个中文文档数据集的对比与选型 |
| [scripts/README.md](scripts/README.md) | 数据集构建工具的用法与 Python 环境要求 |
| [src/test/http/README.md](src/test/http/README.md) | 手工接口测试脚本说明 |

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

本项目基于 [Apache License 2.0](LICENSE) 发布，Copyright 2026 jiyunhe。

`dataset/` 下的数据集涉及第三方文档内容，其版权与许可条款见各数据集 README。
