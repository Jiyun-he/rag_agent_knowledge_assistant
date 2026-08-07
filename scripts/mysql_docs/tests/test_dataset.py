# -*- coding: utf-8 -*-
"""本地 fixture 测试，不依赖真实网络。"""
import json
import re
from pathlib import Path

import pytest


def _make_doc(sample_html, sample_source, terminology, fetched_at="2026-08-06T00:00:00+00:00"):
    from preprocess import extract_document
    return extract_document(sample_html, sample_source, terminology,
                            fetched_at, "rawhash123")


def test_sources_config_parse():
    """1. sources.json 解析：12 核心 + 2 可选，字段与 URL 正确，documentKey 唯一。"""
    from download import SOURCES_PATH
    with open(SOURCES_PATH, "r", encoding="utf-8") as f:
        cfg = json.load(f)
    sources = cfg["sources"]
    core = [s for s in sources if not s.get("optional")]
    opt = [s for s in sources if s.get("optional")]
    assert len(core) == 12
    assert len(opt) == 2
    keys = [s["documentKey"] for s in sources]
    assert len(keys) == len(set(keys))
    for s in sources:
        assert s["enabled"] is True
        assert s["sourceUrl"].startswith("https://mysql.net.cn/doc/refman/8.0/en/")
        assert s["sourceUrl"].endswith(".html")
        assert s["officialUrl"].startswith("https://dev.mysql.com/doc/refman/8.0/en/")
        assert s["language"] == "zh-CN"
        assert s["sourceType"] == "community_translation"


def test_extract_from_local_fixture(sample_html, sample_source, terminology):
    """2. 单个本地 HTML fixture 的正文提取。"""
    doc = _make_doc(sample_html, sample_source, terminology)
    assert doc["title"] == "InnoDB 锁"
    assert doc["content"].strip()
    assert "# InnoDB 锁" in doc["content"]
    assert doc["documentKey"] == "innodb-locking"


def test_structure_preserved(sample_html, sample_source, terminology):
    """3. 标题、段落、列表、表格和代码块保留。"""
    doc = _make_doc(sample_html, sample_source, terminology)
    c = doc["content"]
    assert "## 锁定读类型" in c
    assert "- 锁定读 A" in c
    assert "1. 第一步" in c
    assert "| S | 共享锁 |" in c
    assert "```sql" in c
    assert "SELECT * FROM t WHERE id = 1 LOCK IN SHARE MODE;" in c
    assert "> **注意：** 这是一条提示块内容。" in c
    assert "多版本 相关链接" in c
    assert doc["headingCount"] >= 2
    assert doc["tableCount"] >= 1
    assert doc["codeBlockCount"] >= 1


def test_nav_and_footer_removed(sample_html, sample_source, terminology):
    """4. 导航栏、页脚、脚本被删除。"""
    doc = _make_doc(sample_html, sample_source, terminology)
    c = doc["content"]
    for marker in ("上一页", "下一页", "面包屑", "首页", "Copyright", "document.write"):
        assert marker not in c, f"应删除的内容仍存在: {marker}"


def test_terms_not_applied_in_code(sample_html, sample_source, terminology):
    """5. 术语规范不修改代码块和行内 code。"""
    doc = _make_doc(sample_html, sample_source, terminology)
    raw = doc["rawContent"]
    norm = doc["content"]
    # 正文里的术语被替换
    assert "虚线" not in norm
    assert "幻影行" in norm
    assert "Next-Key Lock（临键锁）" in norm
    assert "一致性非锁定读" in norm
    # rawContent 保留原词
    assert "锁定读取" in raw
    assert "虚线" in raw
    # 代码块内容未被替换
    code_blocks = re.findall(r"```(.*?)```", norm, re.S)
    joined = "\n".join(code_blocks)
    assert "-- 下一键锁定 示例" in joined
    # 行内 code 未被替换
    assert "`下一键锁定`" in norm
    # 替换统计有记录
    assert doc.get("terminologyReplacements", {}).get("虚线", 0) >= 1


def test_json_serialization(sample_html, sample_source, terminology):
    """6. JSON/JSONL 序列化 roundtrip。"""
    doc = _make_doc(sample_html, sample_source, terminology)
    s = json.dumps(doc, ensure_ascii=False)
    doc2 = json.loads(s)
    assert doc2 == doc
    for key in ("documentKey", "title", "spaceKey", "sourceUrl", "officialUrl",
                "rawContent", "content", "contentHash", "characterCount"):
        assert key in doc2


def test_content_hash_stable(sample_html, sample_source, terminology):
    """7. 内容哈希稳定，与抓取时间无关。"""
    a = _make_doc(sample_html, sample_source, terminology, fetched_at="2026-01-01T00:00:00+00:00")
    b = _make_doc(sample_html, sample_source, terminology, fetched_at="2026-06-01T00:00:00+00:00")
    assert a["content"] == b["content"]
    assert a["contentHash"] == b["contentHash"]


def test_repeated_process_idempotent(sample_html, sample_source, terminology, tmp_path):
    """8. 重复运行幂等：产物内容一致。"""
    from preprocess import process_page
    base = tmp_path / "ds"
    (base / "raw" / "html").mkdir(parents=True)
    (base / "raw" / "html" / "innodb-locking.html").write_text(sample_html, encoding="utf-8")

    r1 = process_page(sample_source, terminology, output_dir=base)
    md1 = (base / "processed" / "markdown" / "innodb-locking.md").read_text(encoding="utf-8")
    j1 = json.loads((base / "processed" / "json" / "innodb-locking.json").read_text(encoding="utf-8"))

    r2 = process_page(sample_source, terminology, output_dir=base)
    md2 = (base / "processed" / "markdown" / "innodb-locking.md").read_text(encoding="utf-8")
    j2 = json.loads((base / "processed" / "json" / "innodb-locking.json").read_text(encoding="utf-8"))

    assert md1 == md2
    assert j1 == j2
    assert r1["doc"]["contentHash"] == r2["doc"]["contentHash"]


def test_empty_body_fails(sample_source, terminology):
    """9. 页面正文为空时提取失败。"""
    from preprocess import extract_document
    empty_html = '<html><body><div class="section"></div></body></html>'
    with pytest.raises(ValueError):
        extract_document(empty_html, sample_source, terminology, "t", "h")


def test_single_page_failure_isolated(tmp_path, sample_html, sample_source, terminology):
    """10. 单个页面失败不破坏其他页面结果。"""
    from preprocess import run as preprocess_run
    from preprocess import TERMINOLOGY_PATH
    base = tmp_path / "ds"
    (base / "config").mkdir(parents=True)
    (base / "raw" / "html").mkdir(parents=True)

    ok = dict(sample_source)
    bad = dict(sample_source, documentKey="innodb-phantom-rows")
    cfg = {"datasetName": "test", "sources": [ok, bad]}
    (base / "config" / "sources.json").write_text(
        json.dumps(cfg, ensure_ascii=False), encoding="utf-8")
    (base / "config" / "terminology-map.json").write_text(
        Path(TERMINOLOGY_PATH).read_text(encoding="utf-8"), encoding="utf-8")

    (base / "raw" / "html" / "innodb-locking.html").write_text(sample_html, encoding="utf-8")
    bad_html = '<html><body><div class="section"></div></body></html>'
    (base / "raw" / "html" / "innodb-phantom-rows.html").write_text(bad_html, encoding="utf-8")

    result = preprocess_run(output_dir=base)
    assert result["stats"]["processed"] == 1
    assert result["stats"]["failed"] == 1
    assert (base / "processed" / "markdown" / "innodb-locking.md").exists()
    assert not (base / "processed" / "markdown" / "innodb-phantom-rows.md").exists()