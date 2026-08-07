# -*- coding: utf-8 -*-
"""MyBatis 数据集本地 fixture 测试，不依赖真实网络。"""
import json
import re
from pathlib import Path

import pytest


def _make_doc(sample_html, sample_source, fetched_at="2026-08-07T00:00:00+00:00"):
    from preprocess import extract_document
    return extract_document(sample_html, sample_source, fetched_at, "rawhash123")


def test_sources_config_parse():
    """1. sources.json 解析：8 个 documentKey/expectedTitle/officialUrl 正确且唯一。"""
    from fetch_source import SOURCES_PATH
    with open(SOURCES_PATH, "r", encoding="utf-8") as f:
        cfg = json.load(f)
    sources = cfg["sources"]
    assert len(sources) == 8
    assert cfg["dataset"] == "mybatis-3.5.19-zh"
    assert cfg["sourceVersion"] == "3.5.19"
    keys = [s["documentKey"] for s in sources]
    assert len(keys) == len(set(keys))
    expected = {"introduction", "getting-started", "configuration", "sqlmap-xml",
                "dynamic-sql", "java-api", "statement-builders", "logging"}
    assert set(keys) == expected
    for s in sources:
        assert s["enabled"] is True
        assert s["officialUrl"].startswith("https://mybatis.org/mybatis-3/zh_CN/")


def test_content_root_detection(sample_html):
    """2. 正文根定位：使用 main，不包含导航。"""
    from preprocess import _find_content_root, render_page
    from bs4 import BeautifulSoup
    soup = BeautifulSoup(sample_html, "html.parser")
    root = _find_content_root(soup)
    assert root.name == "main"
    result = render_page(root)
    assert "首页" not in result["markdown"]
    assert "面包屑" not in result["markdown"]


def test_title_extraction(sample_html, sample_source):
    """3. 标题提取：跳过 d-none 模板标题，取可见标题。"""
    doc = _make_doc(sample_html, sample_source)
    assert doc["title"] == "动态 SQL"
    assert "Avoid blank site" not in doc["title"]


def test_metadata_generation(sample_html, sample_source):
    """4. 元数据：frontmatter 与 JSON 字段齐全。"""
    doc = _make_doc(sample_html, sample_source)
    for key in ("documentKey", "title", "spaceKey", "dataset", "sourceVersion",
                "language", "sourceType", "sourceUrl", "fetchedAt", "rawHtmlHash",
                "contentHash", "headingCount", "codeBlockCount", "tableCount",
                "characterCount", "content"):
        assert key in doc, f"missing key {key}"
    assert doc["spaceKey"] == "mybatis-3.5.19-zh"
    assert doc["sourceType"] == "official_website"
    assert doc["sourceUrl"] == sample_source["officialUrl"]


def test_xml_code_block_preserved(sample_html, sample_source):
    """5. XML 代码块完整保留并标注为 xml。"""
    doc = _make_doc(sample_html, sample_source)
    c = doc["content"]
    assert "```xml" in c
    assert '<select id="findActiveBlogWithTitleLike"' in c
    assert "resultType=\"Blog\"" in c
    assert "<if test=\"title != null\">" in c


def test_java_code_block_preserved(sample_html, sample_source):
    """6. Java 代码块完整保留并标注为 java。"""
    doc = _make_doc(sample_html, sample_source)
    c = doc["content"]
    assert "```java" in c
    assert "SqlSession session = sqlSessionFactory.openSession();" in c
    assert "BlogMapper mapper = session.getMapper(BlogMapper.class);" in c


def test_sql_code_block_preserved(sample_html, sample_source):
    """7. SQL 代码块完整保留并标注为 sql。"""
    doc = _make_doc(sample_html, sample_source)
    c = doc["content"]
    assert "```sql" in c
    assert "SELECT * FROM t_user WHERE name = #{name}" in c


def test_table_preserved(sample_html, sample_source):
    """8. Markdown 表格保留为单块（连续 | 行）。"""
    doc = _make_doc(sample_html, sample_source)
    c = doc["content"]
    assert "| 元素 | 作用 |" in c
    assert "| --- | --- |" in c
    assert "| if | 条件判断 |" in c
    assert "| foreach | 循环 |" in c
    # 表格行之间不应被空行拆散
    assert "| 元素 | 作用 |\n| --- | --- |\n| if | 条件判断 |" in c


def test_nav_footer_removed(sample_html, sample_source):
    """9. 导航/页脚/隐藏模板标题被删除。"""
    doc = _make_doc(sample_html, sample_source)
    c = doc["content"]
    for marker in ("首页", "配置</a>", "面包屑", "Avoid blank site", "Apache Software Foundation"):
        assert marker not in c, f"应删除的内容仍存在: {marker}"


def test_hash_stability(sample_html, sample_source):
    """10. 内容哈希稳定，与抓取时间无关。"""
    a = _make_doc(sample_html, sample_source, fetched_at="2026-01-01T00:00:00+00:00")
    b = _make_doc(sample_html, sample_source, fetched_at="2026-06-01T00:00:00+00:00")
    assert a["content"] == b["content"]
    assert a["contentHash"] == b["contentHash"]


def test_json_serialization(sample_html, sample_source):
    """11. JSON 序列化 roundtrip。"""
    doc = _make_doc(sample_html, sample_source)
    doc2 = json.loads(json.dumps(doc, ensure_ascii=False))
    assert doc2 == doc


def test_jsonl_serialization(tmp_path, sample_html, sample_source):
    """12. JSONL 序列化：每行一个 Document，可解析。"""
    from build_dataset import _assemble_jsonl
    base = tmp_path / "ds"
    (base / "processed" / "json").mkdir(parents=True)
    doc = _make_doc(sample_html, sample_source)
    with open(base / "processed" / "json" / "dynamic-sql.json", "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False)
    n = _assemble_jsonl(output_dir=base)
    assert n == 1
    lines = (base / "output" / "documents.jsonl").read_text(encoding="utf-8").strip().split("\n")
    assert len(lines) == 1
    parsed = json.loads(lines[0])
    assert parsed["documentKey"] == "dynamic-sql"


def test_idempotent_rebuild(sample_html, sample_source, tmp_path):
    """13. 重复处理幂等：产物一致。"""
    from preprocess import process_page
    base = tmp_path / "ds"
    (base / "raw" / "html").mkdir(parents=True)
    (base / "raw" / "html" / "dynamic-sql.html").write_text(sample_html, encoding="utf-8")
    r1 = process_page(sample_source, output_dir=base)
    j1 = (base / "processed" / "json" / "dynamic-sql.json").read_text(encoding="utf-8")
    r2 = process_page(sample_source, output_dir=base)
    j2 = (base / "processed" / "json" / "dynamic-sql.json").read_text(encoding="utf-8")
    assert r1["doc"]["contentHash"] == r2["doc"]["contentHash"]
    assert j1 == j2


def test_missing_document_fails(sample_source, tmp_path):
    """14. 缺少 raw html 时 process 失败但不中断其他页面。"""
    from preprocess import run as preprocess_run
    from preprocess import SOURCES_PATH
    base = tmp_path / "ds"
    (base / "config").mkdir(parents=True)
    (base / "raw" / "html").mkdir(parents=True)
    cfg = {"sources": [sample_source, dict(sample_source, documentKey="logging")]}
    (base / "config" / "sources.json").write_text(
        json.dumps(cfg, ensure_ascii=False), encoding="utf-8")
    result = preprocess_run(output_dir=base)
    assert result["stats"]["failed"] == 2
    assert all(r["status"] == "failed" for r in result["results"])


def test_invalid_json_output(tmp_path):
    """15. 空正文 HTML 时提取失败。"""
    from preprocess import extract_document
    empty_html = '<html><body><main><section></section></main></body></html>'
    with pytest.raises(ValueError):
        extract_document(empty_html, {"documentKey": "x"}, "t", "h")


def test_no_chunk_generated(sample_html, sample_source):
    """16. 不生成 Chunk：Document 是单个 content，无 chunk 字段。"""
    doc = _make_doc(sample_html, sample_source)
    assert "chunks" not in doc
    assert "chunkCount" not in doc
    assert "chunkSize" not in doc
    assert isinstance(doc["content"], str)