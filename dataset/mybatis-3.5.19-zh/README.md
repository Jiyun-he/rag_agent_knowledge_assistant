# MyBatis 3.5.19 官方中文参考文档数据集

数据集名称：`mybatis-3.5.19-zh`
数据集版本：`v1`
构建工具：`scripts/mybatis_docs/`
Space Key：`mybatis-3.5.19-zh`

## 1. 数据来源

数据来自 **MyBatis 官方简体中文网站**（`https://mybatis.org/mybatis-3/zh_CN/`），
即官方参考文档的中文版本，由 MyBatis 官方仓库维护并随版本发布。本数据集抓取的是
**官方中文 HTML 页面**，不是第三方教程，不是英文版翻译，不是 MyBatis-Spring /
MyBatis-Plus / Generator / Dynamic SQL 等独立项目的内容。

## 2. 固定版本与 Git Ref

- 对应官方版本：**MyBatis 3.5.19**
- 数据集名称中的 `3.5.19` 表示该文档版本对应的 MyBatis 发布版本
- 官方页面为 Maven Site 生成，内容对应 `mybatis-3.5.19` 官方仓库
  `src/site/zh_CN` 目录下的简体中文文档源文件

本数据集只保留一个固定版本的 8 个核心页面，不追踪 master/main/latest。

## 3. Git Commit

本数据集按 HTML 页面抓取，不直接引入 Git。为便于版本追踪，数据模型的 manifest /
Document 记录了 `datasetVersion` 与 `contentHash`：源站内容变化会导致 `contentHash`
变化，从而可判断某个页面在后续抓取中是否更新（详见“版本更新能力”一节）。

## 4. 8 个 Document

| documentKey | 标题 | 官方 URL |
| --- | --- | --- |
| introduction | 简介 | https://mybatis.org/mybatis-3/zh_CN/ |
| getting-started | 入门 | https://mybatis.org/mybatis-3/zh_CN/getting-started.html |
| configuration | 配置 | https://mybatis.org/mybatis-3/zh_CN/configuration.html |
| sqlmap-xml | XML 映射器 | https://mybatis.org/mybatis-3/zh_CN/sqlmap-xml.html |
| dynamic-sql | 动态 SQL | https://mybatis.org/mybatis-3/zh_CN/dynamic-sql.html |
| java-api | Java API | https://mybatis.org/mybatis-3/zh_CN/java-api.html |
| statement-builders | SQL 语句构建器 | https://mybatis.org/mybatis-3/zh_CN/statement-builders.html |
| logging | 日志 | https://mybatis.org/mybatis-3/zh_CN/logging.html |

只采集这 8 个核心页面，不扩大范围。

## 5. 为什么选择 MyBatis 官方中文文档

- 官方维护，内容质量稳定，结构清晰（正文在 `<main>`，标题层级、代码块、表格齐全）；
- 简体中文，适合作为中文 RAG 评测语料；
- 规模适中（8 个核心页面，含配置、XML 映射、动态 SQL、Java API 等主题），
  覆盖多种文档结构（长表、XML/Java/SQL 代码块、嵌套列表、提示块）；
- 页面结构比第三方站点稳定，便于复现与维护。

## 6. 为什么不用 MySQL 中文镜像作为正式评测语料

仓库中另有 MySQL 8.0 中文文档数据集（`dataset/mysql-innodb-zh/`），其来源
`mysql.net.cn` 是**社区中文镜像**，不是官方中文站点，存在：

- 广告与无关页面元素；
- 机器翻译错误（如正文文本错序）；
- 术语不一致（需要 `terminology-map.json` 显式规范）；
- 站点结构不稳定，需要针对特定 DOM 的提取规则。

而 MyBatis 官方中文站内容质量高、结构稳定，适合作为更干净的评测语料。
两套工具彼此独立，MySQL 工具完整保留，互不依赖。

## 7. 为什么一个官方页面对应一个 Document

MyBatis 每个页面是参考手册的一个独立章节（配置、XML 映射器、动态 SQL……），
有独立标题、完整结构和清晰的语义边界。以页面为 Document 单元，可以：

- 保留章节原始结构（标题层级、代码块、表格）；
- 按章节追溯来源与引用；
- 便于后续以不同策略对相同 Document 重新切分。

## 8. 为什么数据准备阶段不切 Chunk

后续需要在 RAG 主系统中对**完全相同的 Document** 对比多种切分策略：

- 固定长度切分；
- 滑动窗口切分；
- 结构感知切分。

如果数据脚本提前切分，会污染后续实验。因此本工具只产出
`官方 HTML → Document`，**不做 Chunk 切分、不生成 embedding、不写 Qdrant**，
切分职责完全留给 RAG 系统。

## 9. 构建命令

```bash
python scripts/mybatis_docs/build_dataset.py --all             # 下载 + 处理 + 校验
python scripts/mybatis_docs/build_dataset.py --all --force     # 强制重新下载
python scripts/mybatis_docs/build_dataset.py --fetch           # 只下载
python scripts/mybatis_docs/build_dataset.py --process --validate
python scripts/mybatis_docs/build_dataset.py --validate
python -m pytest scripts/mybatis_docs/tests/                   # 测试（本地 fixture，不联网）
```

- 重复运行幂等：fetch 阶段命中 SHA-256 自动跳过，process/validate 覆盖写产物；
- 任一必选 Document 缺失/失败时退出码非 0。

## 10. 输出结构

```text
dataset/mybatis-3.5.19-zh/
├── config/sources.json             8 个页面白名单
├── raw/html/                       {key}.html 原始 HTML（未修改）
├── raw/meta/                       {key}.json 状态码/SHA-256/fetchedAt
├── processed/markdown/             {key}.md 清洗后 Markdown（含 YAML frontmatter）
├── processed/json/                 {key}.json 标准化 Document
└── output/
    ├── documents.jsonl             8 个 Document 汇总（导入 RAG 的主文件）
    ├── manifest.json               数据源清单
    └── quality-report.json         质量检查报告
```

## 11. 数据清洗规则

- 定位正文区域 `<main>`，不包含站点导航/侧边栏/页脚；
- 删除脚本、样式、隐藏元素（`d-none` 等模板标题，如 "Avoid blank site"）与面包屑；
- 保留标题层级、段落、有序/无序列表、表格、代码块、行内代码、提示标签（如 `重要`）；
- 代码块按内容启发式标注语言（xml/java/sql），不修改正文；
- 不进行任何改写、翻译、润色或术语替换；
- 相对链接保留可见文字。

## 12. 元数据

每个 Markdown 文件带 YAML frontmatter；每个 JSON Document 包含：

```json
{
  "documentKey": "configuration",
  "title": "配置",
  "spaceKey": "mybatis-3.5.19-zh",
  "dataset": "mybatis-3.5.19-zh",
  "sourceVersion": "3.5.19",
  "language": "zh-CN",
  "sourceType": "official_website",
  "sourceUrl": "https://mybatis.org/mybatis-3/zh_CN/configuration.html",
  "fetchedAt": "...",
  "rawHtmlHash": "...",
  "contentHash": "...",
  "headingCount": ...,
  "codeBlockCount": ...,
  "tableCount": ...,
  "characterCount": ...,
  "content": "..."
}
```

## 13. 如何导入 RAG

`output/documents.jsonl` 每行是一个 Document。现有 RAG 项目通过
`POST /api/documents` 创建文档：

```json
{
  "spaceId": 1,
  "title": "配置",
  "content": "……documents.jsonl 中该行的 content……",
  "sourceType": "official_website",
  "sourceUri": "https://mybatis.org/mybatis-3/zh_CN/configuration.html"
}
```

系统创建文档时自动调用 TextChunker 切分并写入 chunk，之后走向量化与检索链路。
导入前请先创建知识空间取得 `spaceId`。

## 14. 如何用于后续切分实验

数据准备阶段不切 Chunk，因此你可以对 `documents.jsonl` 中每个 Document 的
`content` 直接应用不同切分策略，例如：

- 固定长度（500 字符）；
- 滑动窗口（500/80 重叠）；
- 结构感知（按标题层级、代码块边界切分）。

由于所有 Document 来自同一份固定版本数据且带 `contentHash`，各实验的输入
一致、结果可对比、可复现。

## 15. 版本更新能力（预留）

数据模型记录了 `documentKey`、`sourceVersion`、`contentHash` 等字段，后续可：

- 抓取不同版本快照（如把 `datasetVersion` 从 v1 升到 v2）并比较每个 Document 的
  `contentHash`，判断 configuration / sqlmap-xml / dynamic-sql 等是否变化；
- 据此测试文档更新、新旧版本索引、active version、旧向量清理、
  MySQL/Qdrant 一致性、异步索引任务恢复。

本工具当前只保留固定版本 v1，不做自动版本 diff。

## 16. 数据文件默认不提交 Git

`raw/`、`processed/`、`output/documents.jsonl` 已在 `.gitignore` 中排除；
`.dataset-cache/` 缓存同样不提交。仓库中提交脚本、`config/sources.json`、README、
示例数据与小型质量报告。

## 17. 许可

MyBatis 文档版权归 MyBatis 项目与 Apache License 2.0 约束。本数据集用于本地评测研究，
使用与再分发前请自行检查并遵守 MyBatis 项目的许可条款。