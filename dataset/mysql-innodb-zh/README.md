# MySQL 8.0 InnoDB 事务与锁（中文）文档数据集

数据集名称：`mysql-8.0-innodb-transaction-locking-zh`
数据集版本：`v1`
构建工具：`scripts/mysql_docs/`

## 1. 数据集用途

本数据集是**离线评测数据准备产物**，不属于线上知识库抓取功能。它从 MySQL 8.0
参考手册的社区中文镜像站点定向采集 14 个 InnoDB 事务与锁相关页面，每个页面
处理为一个标准化 Document，供现有 RAG 项目（知识库问答、检索评测）批量导入使用。

页面聚焦主题：多版本并发控制、锁类型、事务隔离级别、提交与回滚、一致性非锁定读、
锁定读、不同 SQL 语句设置的锁、幻影行、死锁示例、死锁检测、最小化处理死锁、事务调度，
以及两个可选补充页面（InnoDB 事务与锁信息、锁与锁等待信息）。

## 2. 页面选择范围

- 只采集 `config/sources.json` 白名单中 `enabled=true` 的页面，共 12 个核心页面 + 2 个可选页面。
- **不递归爬取网站**、不跟随正文链接、不自动扩展页面范围。
- 默认限速（请求间隔 2 秒），带超时、有限重试与明确 User-Agent。
- 可选页面通过 `--include-optional` 决定是否纳入。

## 3. 中文页面并非 Oracle 官方中文站点

中文页面来源为 `https://mysql.net.cn/doc/refman/8.0/en/`，这是一个**社区中文镜像**，
不是 Oracle 官方中文站点，也不是 Oracle 官方内容。镜像内容可能存在翻译差异、滞后或错误。
因此：

- 每条数据都同时保存对应的官方英文 URL；
- 数据清洗阶段对无法确认的翻译问题只记录警告，不做自动修改；
- 使用本数据集前请自行核对关键内容的准确性。

## 4. 官方英文 URL 的作用

官方英文页面 `https://dev.mysql.com/doc/refman/8.0/en/` 对应 URL 作为
`officialUrl` 字段随每条 Document 保存。其作用是：

- 提供可追溯的原始权威来源，便于人工核对；
- 为术语规范（`terminology-map.json`）提供对照依据；
- 便于后续自动对齐英文与中文版本、补充或修正术语。

## 5. 下载与构建命令

```bash
# 进入脚本目录后（或从仓库根目录执行）
python scripts/mysql_docs/build_dataset.py --all          # 下载 + 处理 + 校验（12 核心页）
python scripts/mysql_docs/build_dataset.py --all --include-optional   # 含 2 个可选页
python scripts/mysql_docs/build_dataset.py --all --force              # 强制重新下载
python scripts/mysql_docs/build_dataset.py --download                 # 只下载
python scripts/mysql_docs/build_dataset.py --process --validate       # 只处理 + 校验
python scripts/mysql_docs/build_dataset.py --validate                 # 只校验
```

- 重复运行具有幂等性：下载阶段命中 SHA-256 / HTTP 304 自动跳过，处理与校验阶段覆盖写产物。
- 任一必选页面失败时进程退出码非 0；可选页面失败只产生警告。
- 运行测试：`python -m pytest scripts/mysql_docs/tests/`（使用本地 fixture，不依赖网络）。

## 6. 输出目录

```text
dataset/mysql-innodb-zh/
├── config/
│   ├── sources.json          页面白名单与请求配置
│   └── terminology-map.json  术语显式规范映射
├── raw/
│   ├── html/                 {key}.html 原始 HTML（未修改）
│   └── meta/                 {key}.json 状态码/ETag/Last-Modified/响应时间/SHA-256
├── processed/
│   ├── markdown/             {key}.md 清洗后 Markdown（含 YAML 头部）
│   └── json/                 {key}.json 标准化 Document
└── output/
    ├── documents.jsonl       全部 Document 汇总（导入 RAG 的主文件）
    ├── manifest.json         数据源清单（页面数、哈希、文件路径、质量标记）
    └── quality-report.json   数据质量检查报告
```

## 7. 数据清洗规则

正文提取基于 DOM 结构（正文区域 `div.section` / `main` / `article`），不使用整页
`get_text()`。清洗规则：

- 保留标题层级、段落、有序/无序列表、表格、`pre`/`code` 代码块、行内代码、Note/Warning/Tip 提示块；
- 保留交叉引用的可见文字；
- 删除页面导航、菜单、面包屑、搜索框、页眉、页脚、上一页/下一页/目录跳转、script/style/广告；
- 删除重复版权声明与站点公共模板文本；
- 相对链接只保留链接文字；
- 规范连续空格、空行与 HTML 实体；
- **不修改 SQL、类名、变量名、配置项和代码内容**。

## 8. 术语规范规则

中文页面保持原文，不进行自动全文翻译。每页生成两份正文：

- `rawContent`：从正文区域直接提取、仅完成格式清理的内容；
- `content`（normalizedContent）：在 `rawContent` 基础上按 `config/terminology-map.json`
  执行**显式配置的有限术语替换**后的内容，作为实际导入 RAG 的正文。

约束：

- 术语替换不作用于代码块，不修改 SQL 标识符；
- 每次替换记录原词、目标词与次数（写入 Document 的 `terminologyReplacements` 与日志）；
- 不做大范围语言润色；对无法确认的翻译问题只记录警告，不自动修改。

## 9. 如何导入现有 RAG 项目

`output/documents.jsonl` 每行是一个 JSON Document，其中 `content` 字段为规范化后的
Markdown 正文，`title` 为页面标题。现有 RAG 项目通过
`POST /api/documents` 创建文档：

```json
{
  "spaceId": 1,
  "title": "InnoDB 锁",
  "content": "……documents.jsonl 中该行的 content……",
  "sourceType": "community_translation",
  "sourceUri": "https://mysql.net.cn/doc/refman/8.0/en/innodb-locking.html"
}
```

系统创建文档时会自动调用 TextChunker 进行切分并写入 chunk，之后可走向量化与检索链路。
导入前请先创建知识空间并取得 `spaceId`。可参考仓库 `evaluation/` 下的导入示例。

## 10. 为什么每个页面作为一个 Document

这些页面都是 MySQL 参考手册的独立章节，每页有独立标题、完整结构和清晰的语义边界。
以页面为单位保存 Document 可以保留章节的原始结构（标题层级、代码块、表格），
便于后续按不同策略重新切分、以及按章节追溯来源与引用。

## 11. 为什么脚本不提前执行 Chunk 切分

当前 RAG 项目的 `TextChunker` 是固定长度切分，**并非最终切分策略**。切分策略后续
可能调整（例如基于标题层级或语义结构切分）。因此本脚本只保存结构完整的规范化正文，
把切分职责留给 RAG 系统本身——将来更换切分策略时，可以直接基于这份保留结构的
`content` 重新切分，无需重新下载与清洗原始数据。同理，本脚本不生成 embedding、
不写入 Qdrant、不修改 MySQL 业务数据。

## 12. 数据文件默认不提交 Git

`raw/`（原始 HTML 与响应元数据）与 `processed/`（清洗后 Markdown/JSON）属于完整抓取
内容，已在 `.gitignore` 中排除；`output/documents.jsonl` 同样不入库。
仓库中只提交：脚本、`config/sources.json`、`config/terminology-map.json`、README、
示例数据与小型质量报告。如需在团队内分发完整数据，请通过数据集目录整体打包（zip）等
方式，而非通过 Git。

## 13. 许可条款

本数据集内容来自 MySQL 参考手册。MySQL 文档的使用、复制与再分发受其文档许可条款约束
（可查看官网关于文档许可的说明）。使用、加工或再分发本数据集前，请**自行检查并遵守**
相应的文档许可条款。本仓库不提供任何许可保证。