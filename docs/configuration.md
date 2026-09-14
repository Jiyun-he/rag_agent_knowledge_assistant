# 配置说明

本文说明依赖服务、环境变量与 `application.yml` 的完整配置项。
其他文档：[架构](architecture.md) · [数据模型](data-model.md) · [API](api.md) · [检索](retrieval.md) · [评测](evaluation.md) · [返回根 README](../README.md)

## 依赖服务

`docker-compose.yml` 定义了以下服务：

| 服务 | 端口 | 用途 | 当前状态 |
| --- | --- | --- | --- |
| `mysql` | 3306 | 结构化业务数据、索引任务、问答历史、评测记录 | 必需 |
| `qdrant` | 6333 / 6334 | chunk 向量存储与相似度检索 | 必需 |
| `elasticsearch` | 9200 | 关键词索引（镜像内已装 `analysis-smartcn` 中文分词插件，见 `docker/elasticsearch/Dockerfile`） | 必需 |
| `reranker` | 8081 | TEI（Text Embeddings Inference），提供 `/rerank` 接口 | 必需（`HYBRID_RERANK` 模式） |
| `redis` | 6379 | — | **暂未使用**，为后续「Redis 缓存高频问题或检索结果」预留 |
| `rabbitmq` | 5672 / 15672 | — | **暂未使用**，为后续「以消息队列替代任务表轮询调度」预留 |

启动全部服务：

```bash
docker compose up -d
```

`reranker` 首次启动需要下载 `BAAI/bge-reranker-v2-m3` 模型，数据保存在 `rag_rerank_model` 卷中，后续启动可直接复用。

## 环境变量

敏感配置通过环境变量注入。仓库采用 `spring-dotenv`（`me.paulschwarz:spring-dotenv`），应用启动时会自动加载根目录的 `.env` 文件：

```dotenv
# OpenAI 兼容服务的 API Key（必填）
OPENAI_API_KEY=your_api_key_here

# OpenAI 兼容服务的 Base URL
OPENAI_BASE_URL=https://api.openai.com

# 模型名（覆盖 application.yml 中的默认值）
OPENAI_CHAT_MODEL=gpt-4o-mini
OPENAI_EMBEDDING_MODEL=text-embedding-3-small
```

`OPENAI_BASE_URL` 可指向官方 OpenAI，也可指向兼容 OpenAI 接口格式的第三方服务（如 DashScope、SiliconFlow）或本地推理服务（如 vLLM）。

> `.env` 已在 `.gitignore` 中排除，不会进入版本库。
>
> 更换 embedding 模型会改变向量维度，必须同步更换 Qdrant `collection-name` 或删除旧 collection。

## `application.yml` 完整配置

```yaml
server:
  port: 8080

spring:
  application:
    name: rag-agent-knowledge-assistant

  task:
    scheduling:
      # @Scheduled 线程池：worker 轮询、RUNNING 回收、周期对账可并行，避免互相阻塞
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
      encoding: UTF-8
      continue-on-error: false

  data:
    elasticsearch:
      uris: http://localhost:9200

  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}

      chat:
        options:
          model: ${OPENAI_CHAT_MODEL:gpt-4o-mini}
          temperature: 0.3

      embedding:
        options:
          # 换 embedding 模型会改变向量维度，需同步更换 Qdrant collection-name 或删除旧 collection
          model: ${OPENAI_EMBEDDING_MODEL:text-embedding-3-small}

    vectorstore:
      qdrant:
        host: localhost
        port: 6334
        collection-name: rag_kb_chunks
        use-tls: false
        initialize-schema: true

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    banner: false
    db-config:
      id-type: auto

springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs

rag:
  chunk:
    # FIXED_SIZE 的长度参数按「字符」计
    strategy: FIXED_SIZE
    max-size: 500
    overlap-size: 80
    # STRUCTURE_AWARE 的长度参数按「token」计（jtokkit cl100k_base）
    structure-aware:
      target-size: 800
      hard-limit: 1200
      overlap-size: 150
      context-max: 100

  rerank:
    base-url: http://localhost:8081

  # 检索参数
  retrieval:
    # 送入重排模型的候选数上限（HYBRID_RERANK）；与 candidateK 解耦，可单独作为实验变量
    rerank-candidate-top-m: 30
    # 向量检索 topK 上限：需覆盖 candidateK 的可用范围，否则 candidateK 在向量侧会被静默截断
    vector-max-top-k: 50

  # 周期索引对账（兜底清理 Qdrant/ES 孤儿数据 + 补建缺失索引），默认每天 03:00
  reconciliation:
    cron: 0 0 3 * * *

  # 持久化异步索引任务机制
  task:
    # worker 轮询可执行任务（PENDING / 到期 RETRY_WAIT）的间隔
    worker-poll-ms: 2000
    worker-initial-delay-ms: 5000
    worker-batch-size: 10
    # RUNNING 超时回收的扫描间隔
    recover-poll-ms: 30000
    # RUNNING 任务超时阈值：进程被杀后遗留的 RUNNING 会被回收重排
    running-timeout-ms: 1800000
    # 失败重试：指数退避（base × 2^n，上限 max），max-retry-times 次后置 FAILED
    max-retry-times: 5
    retry-base-delay-ms: 1000
    retry-max-delay-ms: 60000
```

## 关键配置项说明

### 切分策略 `rag.chunk`

| 配置项 | 默认值 | 单位 | 说明 |
| --- | --- | --- | --- |
| `strategy` | `FIXED_SIZE` | — | 切分策略，取值 `FIXED_SIZE` 或 `STRUCTURE_AWARE` |
| `max-size` | 500 | 字符 | `FIXED_SIZE` 的 chunk 长度下限为 100（代码内 `Math.max(100, maxSize)`） |
| `overlap-size` | 80 | 字符 | `FIXED_SIZE` 的相邻 chunk 重叠长度，上限为 `max-size / 2` |
| `structure-aware.target-size` | 800 | token | `STRUCTURE_AWARE` 的目标 chunk 长度，同 headingPath 下的段落合并至接近该值 |
| `structure-aware.hard-limit` | 1200 | token | `STRUCTURE_AWARE` 的硬上限，超过则强制断开 |
| `structure-aware.overlap-size` | 150 | token | `STRUCTURE_AWARE` 的重叠长度 |
| `structure-aware.context-max` | 100 | token | 代码块/表格等结构单元可携带的邻接正文字数上限 |

> 两套策略的长度单位不同：`FIXED_SIZE` 按**字符**计数（`String.length()`），`STRUCTURE_AWARE` 按 **token** 计数（jtokkit，`cl100k_base` 编码，与 `text-embedding-3-small` 的切词一致）。调整参数时注意区分——本语料实测约 1 个汉字 ≈ 1 个 token，两者数值恰好接近，容易被误认为同一单位。


`STRUCTURE_AWARE` 先用 commonmark + gfm-tables 把 Markdown 解析成 AST，再按标题、段落、代码块、表格边界切分，并在每个 chunk 开头附加完整标题路径（如 `# 配置` + `## 属性（properties）`）。两种策略的实测对比见[评测结果](../evaluation/mybatis-eval/RESULTS.md)。

### 检索 `rag.retrieval`

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `rerank-candidate-top-m` | 30 | `HYBRID_RERANK` 送入重排模型的候选数上限，实际取 `max(该值, topK)` |
| `vector-max-top-k` | 50 | 向量检索 topK 上限，需覆盖 `candidateK` 的取值范围 |

### 重排 `rag.rerank`

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `base-url` | `http://localhost:8081` | TEI 重排服务地址，调用其 `/rerank` 接口 |

### 索引任务 `rag.task`

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `worker-poll-ms` | 2000 | worker 轮询可执行任务（PENDING / 到期 RETRY_WAIT）的间隔 |
| `worker-initial-delay-ms` | 5000 | worker 首次执行的延迟 |
| `worker-batch-size` | 10 | worker 单批拉取的任务数 |
| `recover-poll-ms` | 30000 | `RUNNING` 超时回收的扫描间隔 |
| `running-timeout-ms` | 1800000 | `RUNNING` 任务超时阈值，超时后回收重排（30 分钟） |
| `max-retry-times` | 5 | 失败任务最大重试次数，超过后置 `FAILED` |
| `retry-base-delay-ms` | 1000 | 重试退避基数（base × 2^n） |
| `retry-max-delay-ms` | 60000 | 重试退避上限（60s） |

### 周期对账 `rag.reconciliation`

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `cron` | `0 0 3 * * *` | 周期索引对账的执行时间，默认每天 03:00 |

## schema 脚本

建表脚本 `src/main/resources/db/schema.sql` 通过 `spring.sql.init.mode=always` 以 `CREATE TABLE IF NOT EXISTS` 方式执行，**不会修改已存在的表结构**。表结构发生变更后，需要重建开发库或手工执行 `ALTER`。
