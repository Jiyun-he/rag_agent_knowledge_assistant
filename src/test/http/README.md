# 手工接口测试脚本

本目录存放以 `.http` 格式编写的接口调用脚本，用于在开发过程中手工验证接口行为。它们**不会**在构建或 CI 中自动执行，需要由使用者在 IDE 中逐个发送请求。

## 使用方式

脚本为标准的 `.http` 格式，IntelliJ IDEA 内置的 HTTP Client 可直接运行，VS Code 安装 REST Client 扩展后亦可。

脚本中的 `@host = http://localhost:8080` 等变量可在文件顶部调整。部分请求路径（如 `/api/rag/index-tasks/1`）中的 ID 是**示例占位值**，需要先执行前置请求取得真实 ID 再替换，否则会命中不存在的记录。文件内已有相应注释提示。

## 文件说明

| 文件 | 覆盖内容 |
| --- | --- |
| `hybridSeach.http` | 四种检索模式各一次（`VECTOR_ONLY` / `KEYWORD_ONLY` / `HYBRID` / `HYBRID_RERANK`），外加一次使用 `HYBRID_RERANK` 的 ask |
| `eval.http` | 创建评测集 → 添加评测 case → 查询 case → 启动 `VECTOR_ONLY` / `HYBRID_RERANK` 评测 → 查询 run 汇总 → 查询每个 case 的结果 → 对比多个 run |
| `week7.http` | 较完整的端到端链路：创建空间、创建文档、触发索引任务、查询任务、检索、`/api/rag/ask`（有匹配上下文 / 无明显匹配）、以及缺少 `spaceId`、空问题两类参数校验 |
| `week6.http` | 持久化索引任务：手动触发 `BUILD_INDEX`、按 taskId 查询、查询文档的任务列表、重试 `FAILED` 任务 |
| `week5.http` | 问答历史：新建会话提问、复用已有会话、按 spaceId 查询会话、按 sessionId 查询消息、按 messageId 查询引用、查询完整会话历史 |
| `week4.http` | RAG Ask 的两个基本场景 |
| `week3.http` | 健康检查、为文档构建索引、知识空间内检索 |
| `week2.http` | 知识空间与文档的 CRUD、文档 chunk 列表查询、更新文档后重新查看 chunk |
| `api-test.http` | 创建知识文档的最小示例 |

## 关于 `week*.http`

`week2` – `week7` 按开发周次命名，是课程/迭代期间逐步补充的历史脚本，保留用于回顾当时的接口形态。它们与较新的 `hybridSeach.http`、`eval.http` 存在部分重叠。

**需要完整的、按顺序执行的端到端流程时，优先使用 [`evaluation/eval-http/`](../../../evaluation/eval-http/)**：那是一套带编号步骤、明确标注占位 ID 来源的导入与评测脚本，覆盖从创建知识空间、导入 8 篇演示语料到构建评测集、对比四种检索模式的完整链路。说明见 [`evaluation/README.md`](../../../evaluation/README.md)。

单元测试与端到端测试见根 [README](../../../README.md#测试)。
