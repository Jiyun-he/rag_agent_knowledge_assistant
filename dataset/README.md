# 中文文档数据集

本目录存放两个用于 RAG 评测的中文技术文档数据集，均由 [`scripts/`](../scripts/README.md) 下的工具离线构建。每个数据集的来源、清洗规则、字段定义与许可条款见各自的 README。

## 数据集对比

| | `mybatis-3.5.19-zh` | `mysql-innodb-zh` |
| --- | --- | --- |
| 数据集名称 | `mybatis-3.5.19-zh` | `mysql-8.0-innodb-transaction-locking-zh` |
| 主题 | MyBatis 3.5.19 官方参考文档 | MySQL 8.0 InnoDB 事务与锁 |
| 来源 | **官方**简体中文站 `mybatis.org/mybatis-3/zh_CN/` | **社区中文镜像** `mysql.net.cn`（非 Oracle 官方） |
| 页面数 | 8 个核心页 | 12 个核心页 + 2 个可选页 |
| 版本 | 固定 3.5.19，不追踪 latest | 固定 MySQL 8.0 参考手册 |
| 构建工具 | `scripts/mybatis_docs/` | `scripts/mysql_docs/` |
| 术语规范 | 无需（官方中文） | 依赖 `config/terminology-map.json` 显式规范 |
| 语料质量 | 高：官方维护、结构稳定、无机器翻译错误 | 中：社区镜像，存在广告元素、翻译差异与滞后，需人工核对 |
| 用途定位 | **正式评测语料** | 对比样本与更复杂清洗规则的验证 |

## 选型建议

**正式评测用 `mybatis-3.5.19-zh`。** 它来自官方中文站点，内容质量稳定，页面结构清晰（正文在 `<main>`，标题层级、代码块、表格齐全），覆盖面包含长表、XML/Java/SQL 代码块、嵌套列表等多样结构。基于它构建的 26 题评测集与实验结果见 [`evaluation/mybatis-eval/RESULTS.md`](../evaluation/mybatis-eval/RESULTS.md)。

**`mysql-innodb-zh` 保留作为对照。** 社区镜像带来的问题（广告与无关页面元素、机器翻译错误、术语不一致、站点 DOM 结构不稳定）使它不适合做主评测语料，但它覆盖了官方中文站不具备的场景：需要针对特定 DOM 的提取规则、需要显式术语映射、需要同时保存官方英文 `officialUrl` 做追溯。使用前请自行核对关键内容的准确性。

## 共同约定

两个数据集由独立工具构建，但遵循同一套约定：

- **输出目录结构一致**：`config/`（页面白名单，MySQL 侧另有术语映射）、`raw/`（原始 HTML 与抓取元信息）、`processed/`（清洗后 Markdown 与标准化 Document）、`output/`（`documents.jsonl`、`manifest.json`、`quality-report.json`）。
- **一个页面 = 一个 Document**：保留章节的原始结构、独立标题与清晰语义边界，便于按章节追溯来源，也便于后续以不同策略对相同 Document 重新切分。
- **数据准备阶段不切 chunk**：只产出 `官方页面 → Document`，不做切分、不生成 embedding、不写 Qdrant。切分职责完全留给 RAG 主系统，避免污染切分策略对比实验。
- **大文件不入库**：`raw/`、`processed/`、`output/documents.jsonl` 与 `.dataset-cache/` 已在 `.gitignore` 中排除，仓库只提交脚本、`config/`、README 与小型质量报告。
- **导入方式相同**：`output/documents.jsonl` 每行一个 Document，通过 `POST /api/documents` 导入，先创建知识空间取得 `spaceId`。

## 如何导入

先按 [`scripts/README.md`](../scripts/README.md) 运行对应工具生成 `output/documents.jsonl`，再将其中的 Document 逐条提交到 RAG 系统：

```json
{
  "spaceId": 1,
  "title": "配置",
  "content": "……documents.jsonl 中该行的 content……"
}
```

导入后系统自动切分并登记索引任务，等待文档状态变为 `ACTIVE` 后即可检索。完整的导入与验证流程见 [`evaluation/eval-http/01_import_docs_and_basic_test.http`](../evaluation/eval-http/01_import_docs_and_basic_test.http)。
