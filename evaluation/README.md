# 评测资源

本目录存放 RAG 评测相关的语料、可执行脚本、人工标注与实验结果。评测指标的定义见 [`docs/evaluation.md`](../docs/evaluation.md)，评测模块的接口见 [`docs/api.md`](../docs/api.md#rag-evaluation-接口)。

## 目录导航

| 目录 | 内容 | 性质 |
| --- | --- | --- |
| `rag-docs/` | 8 篇 Markdown 语料 | **喂给 RAG 系统的演示语料**，不是项目文档 |
| `eval-http/` | 2 个 `.http` 脚本 | 手工执行的端到端流程：导入语料、验证检索、构建评测集 |
| `mybatis-eval/` | `RESULTS.md` + `annotation/` | MyBatis 语料上的正式实验结果与人工标注 |

## `rag-docs/`：演示语料

这 8 篇文档（`01_project_overview.md` 到 `08_rag_evaluation_module.md`）描述的是**本项目自身**的模块设计，但它们的作用不是项目文档，而是**导入 RAG 系统的演示语料**。

它们与 [`eval-http/01_import_docs_and_basic_test.http`](eval-http/01_import_docs_and_basic_test.http) 的第 2.1–2.8 步**逐一对应**——脚本中每一步创建一篇文档，`content` 字段即该文件的正文内容。

> 注意与 `docs/` 的区别：`docs/` 下是给人读的设计文档，`rag-docs/` 下是给系统检索的语料。两者内容主题相近，用途完全不同。

## `eval-http/`：可执行流程

| 文件 | 覆盖流程 |
| --- | --- |
| `01_import_docs_and_basic_test.http` | 健康检查 → 创建知识空间 → 创建 8 篇文档（2.1–2.8）→ 查看文档列表与 chunks → 手动触发/查询索引任务 → 四种检索模式各一次 → RAG ask（有答案 / 无答案）→ 查询问答会话 → 创建评测数据集 |
| `02_eval_cases_template_after_chunk_ids.http` | 12 个 EvalCase 模板 → 分别运行 `VECTOR_ONLY` / `KEYWORD_ONLY` / `HYBRID` / `HYBRID_RERANK` 四种 EvalRun → 对比多个 EvalRun |

两个文件都需要**人工替换占位 ID**后才能运行，文件头部已写明对应关系：

| 占位符 | 来源 |
| --- | --- |
| `@spaceId` | 01 第 1 步创建知识空间的响应 `data.id` |
| `@doc1` – `@doc8` | 01 第 2.1–2.8 步各文档的响应 `data.id` |
| `@taskId` | 01 第 6.x 步查询索引任务的响应 `data.taskId` |
| `@datasetId` | 01 第 14 步创建评测数据集的响应 `data.id` |
| `expectedChunkIds` | 01 第 4.x 步查看 chunks 得到的真实 chunkId |

`expectedChunkIds` 是评测的 ground truth，必须填真实 chunkId；填错会让所有指标失真。

## `mybatis-eval/`：实验结果

- `RESULTS.md` — 基于 MyBatis 3.5.19 官方中文文档（8 篇，约 12.4 万字）与 26 个 EvalCase 的完整实验：实验条件、两组切分参数、分块策略对比、检索策略对比、局限与复现步骤。
- `annotation/mybatis_26_questions_chunk_ids_updated.xlsx` — 26 题的人工标注文件，两个知识空间（定长切分 / 结构感知切分）分别标注期望 chunkId。
- `node_modules/` — 标注工具遗留的符号链接（指向运行时缓存），不是项目依赖，已被 `.gitignore` 排除。

## 相关链接

- 评测指标定义、NULL 语义、评测流程：[`docs/evaluation.md`](../docs/evaluation.md)
- 评测接口清单：[`docs/api.md`](../docs/api.md#rag-evaluation-接口)
- 评测所用语料的构建方式：[`dataset/README.md`](../dataset/README.md) 与 [`scripts/README.md`](../scripts/README.md)
