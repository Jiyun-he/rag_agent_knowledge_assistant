# 数据集构建工具

本目录存放两套**彼此独立**的 Python 工具，各自负责从中文技术文档站点抓取并构建一个可导入 RAG 系统的评测语料集。两套工具互不依赖，可分别运行。

| 工具 | 数据来源 | 产出数据集 | 页面数 |
| --- | --- | --- | --- |
| `mybatis_docs/` | MyBatis 官方简体中文站（`mybatis.org/mybatis-3/zh_CN/`） | `dataset/mybatis-3.5.19-zh/` | 8 个核心页 |
| `mysql_docs/` | MySQL 社区中文镜像（`mysql.net.cn`） | `dataset/mysql-innodb-zh/` | 12 个核心页 + 2 个可选页 |

各数据集的来源、许可、清洗规则与字段定义见 [`dataset/mybatis-3.5.19-zh/README.md`](../dataset/mybatis-3.5.19-zh/README.md) 与 [`dataset/mysql-innodb-zh/README.md`](../dataset/mysql-innodb-zh/README.md)。数据集之间的对比与选型建议见 [`dataset/README.md`](../dataset/README.md)。

## 目录结构

```text
scripts/
├── mybatis_docs/
│   ├── build_dataset.py     统一命令行入口（fetch → process → validate）
│   ├── fetch_source.py      下载官方页面，记录 SHA-256 与状态码
│   ├── preprocess.py        提取正文并生成标准化 Document
│   ├── validate.py          质量检查
│   └── tests/               pytest 测试（本地 fixture，不联网）
└── mysql_docs/
    ├── build_dataset.py     统一命令行入口（download → process → validate）
    ├── download.py          下载页面，记录 ETag / Last-Modified / SHA-256
    ├── preprocess.py        按 DOM 提取正文、应用术语规范
    ├── validate.py          质量检查
    └── tests/               pytest 测试（本地 fixture，不联网）
```

## 共同约定

- **三段式流程**：抓取 → 处理 → 校验，三个阶段可独立执行，也可用 `--all` 一次跑完。
- **输出目录约定**：每个数据集都遵循同一套结构——
  - `config/`：页面白名单与请求配置（MySQL 侧另有 `terminology-map.json` 术语规范映射）
  - `raw/html/`、`raw/meta/`：原始 HTML 与抓取元信息（状态码、SHA-256、抓取时间）
  - `processed/markdown/`、`processed/json/`：清洗后的 Markdown（含 YAML frontmatter）与标准化 Document
  - `output/`：`documents.jsonl`（导入 RAG 的主文件）、`manifest.json`、`quality-report.json`
- **幂等**：重复运行安全。抓取阶段命中 SHA-256（MySQL 侧还有 ETag / HTTP 304）自动跳过，可用 `--force` 强制重下；处理与校验阶段覆盖写产物。
- **退出码**：`0` 成功；`1` 任一必选页面缺失或失败；`2` 参数错误。
- **不提前切 chunk**：两套工具都只产出 `官方页面 → Document`，不做 chunk 切分、不生成 embedding、不写 Qdrant。切分职责完全留给 RAG 主系统，以便对**完全相同的 Document** 对比多种切分策略而不污染实验。
- **大文件不入库**：`raw/`、`processed/`、`output/documents.jsonl` 与 `.dataset-cache/` 均在 `.gitignore` 中排除，仓库只提交脚本、`config/`、README 与小型质量报告。

## Python 环境

- **Python 3.12**
- 第三方依赖仅两个：`beautifulsoup4`（正文提取）与 `pytest`（测试）。其余全部为标准库。
- 仓库中**没有** `requirements.txt` / `pyproject.toml` / `pytest.ini`，需要自行安装：

```bash
pip install beautifulsoup4 pytest
```

- 抓取阶段需要访问外网；`tests/` 使用本地 fixture，不联网。

## 常用命令

```bash
# MyBatis 数据集
python scripts/mybatis_docs/build_dataset.py --all              # 抓取 + 处理 + 校验
python scripts/mybatis_docs/build_dataset.py --all --force      # 强制重新抓取
python scripts/mybatis_docs/build_dataset.py --fetch            # 只抓取
python scripts/mybatis_docs/build_dataset.py --process --validate
python scripts/mybatis_docs/build_dataset.py --validate

# MySQL 数据集
python scripts/mysql_docs/build_dataset.py --all                # 抓取 + 处理 + 校验
python scripts/mysql_docs/build_dataset.py --all --include-optional   # 含 2 个可选页
python scripts/mysql_docs/build_dataset.py --all --force
python scripts/mysql_docs/build_dataset.py --download           # 只抓取
python scripts/mysql_docs/build_dataset.py --process --validate
python scripts/mysql_docs/build_dataset.py --validate
```

两套工具的 `build_dataset.py` 都支持 `--output-dir` 指定输出目录。

## 测试

```bash
python -m pytest scripts/mybatis_docs/tests/
python -m pytest scripts/mysql_docs/tests/
```

测试使用本地 HTML fixture，不依赖网络。
