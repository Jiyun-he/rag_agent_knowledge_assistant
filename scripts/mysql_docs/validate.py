# -*- coding: utf-8 -*-
"""数据质量检查。

对 processed/json 中的每个 Document 执行质量检查，输出 quality-report.json
并在控制台打印易读摘要。检查覆盖：HTTP 状态、正文/标题非空、标题匹配、
InnoDB 关键词、中文字符占比、正文长度、导航/页脚残留、代码块保留、标题层级、
Markdown/JSON 可读、documentKey 唯一、内容哈希重复告警、术语替换日志、整体完整性。
"""
from __future__ import annotations

import argparse
import json
import logging
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

LOG = logging.getLogger("mysql_docs")

REPO_ROOT = Path(__file__).resolve().parents[2]
DATASET_DIR = REPO_ROOT / "dataset" / "mysql-innodb-zh"
CONFIG_DIR = DATASET_DIR / "config"
SOURCES_PATH = CONFIG_DIR / "sources.json"
TERMINOLOGY_PATH = CONFIG_DIR / "terminology-map.json"
PROCESSED_JSON_DIR = DATASET_DIR / "processed" / "json"
PROCESSED_MARKDOWN_DIR = DATASET_DIR / "processed" / "markdown"
OUTPUT_DIR = DATASET_DIR / "output"
QUALITY_REPORT_PATH = OUTPUT_DIR / "quality-report.json"

MIN_CHARACTER_COUNT = 200
MIN_ZH_RATIO = 0.05
# 注意：导航标记只保留导航专用词。"搜索""目录"等在技术正文中会合法出现（如"搜索条件"），不作为导航信号。
NAV_MARKERS = ("上一页", "下一页", "面包屑", "打印本页")
FOOTER_MARKERS = ("版权所有", "版权声明", "Copyright (c)")
MAX_NAV_HITS = 3
MAX_FOOTER_HITS = 1

_ZH_RE = re.compile(r"[\u4e00-\u9fff]")
_CODE_BLOCK_RE = re.compile(r"```.*?```", re.S)
_INLINE_CODE_RE = re.compile(r"`[^`\n]*`")


def _non_code_text(text: str) -> str:
    """\u53bb\u6389\u56f4\u680f\u4ee3\u7801\u5757\u4e0e\u884c\u5185\u4ee3\u7801\u540e\u5269\u4f59\u6587\u672c\uff0c\u7528\u4e8e\u68c0\u67e5\u672f\u8bed\u662f\u5426\u6b8b\u7559\u4e8e\u975e\u4ee3\u7801\u6587\u672c\u3002"""
    t = _CODE_BLOCK_RE.sub(" ", text)
    t = _INLINE_CODE_RE.sub(" ", t)
    return t


def _zh_ratio(text: str) -> float:
    if not text:
        return 0.0
    zh = len(_ZH_RE.findall(text))
    total = max(1, len(re.sub(r"\s+", "", text)))
    return zh / total


def _title_similar(title: str, expected: str) -> bool:
    """标题基本匹配：忽略空白后的包含关系，或编辑距离 <= 4（容错镜像站点微调）。"""
    if not expected:
        return bool(title)
    if expected in title or title in expected:
        return True
    # 忽略空白后判断包含关系（镜像标题常缺少空格，如"不同SQL语句设置的锁"）
    t_ns = re.sub(r"\s+", "", title)
    e_ns = re.sub(r"\s+", "", expected)
    if e_ns in t_ns or t_ns in e_ns:
        return True
    # 简单编辑距离
    a, b = t_ns, e_ns
    if abs(len(a) - len(b)) > 4:
        return False
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1] <= 4


def _check(checks: list[dict], name: str, passed: bool, detail: str = "") -> None:
    checks.append({"name": name, "pass": bool(passed), "detail": detail})


def validate_document(doc: dict, source: dict, term_map: dict,
                      markdown_path: Path, meta_path: Path) -> dict:
    """对单个 Document 执行检查。返回检查结果。"""
    checks: list[dict] = []
    warnings: list[str] = []
    content = doc.get("content", "")
    raw_content = doc.get("rawContent", "")
    key = doc.get("documentKey", "?")

    # 1. HTTP 状态（读取 raw meta；meta 缺失时按跳过处理，不判失败）
    http_status = None
    if meta_path.exists():
        try:
            with open(meta_path, "r", encoding="utf-8") as f:
                http_status = json.load(f).get("httpStatus")
        except (OSError, json.JSONDecodeError):
            http_status = None
    _check(checks, "httpStatus200",
           http_status is None or http_status == 200,
           f"http={http_status}" if http_status else "n/a (无 raw meta)")
    _check(checks, "bodyNonEmpty", bool(content and content.strip()),
           f"len={len(content)}")
    _check(checks, "titleNonEmpty", bool(doc.get("title", "").strip()),
           doc.get("title", "")[:40])
    _check(checks, "titleMatchesExpected",
           _title_similar(doc.get("title", ""), source.get("expectedTitle", "")),
           f"expected={source.get('expectedTitle', '')}")
    _check(checks, "containsInnoDB", "InnoDB" in content,
           "InnoDB" if "InnoDB" in content else "not found")
    zh = _zh_ratio(content)
    _check(checks, "zhRatioOk", zh >= MIN_ZH_RATIO, f"ratio={zh:.3f}")
    _check(checks, "lengthOk", len(content) >= MIN_CHARACTER_COUNT,
           f"len={len(content)}")
    nav_hits = sum(content.count(m) for m in NAV_MARKERS)
    _check(checks, "noExcessiveNav", nav_hits <= MAX_NAV_HITS, f"nav_hits={nav_hits}")
    footer_hits = sum(content.count(m) for m in FOOTER_MARKERS)
    _check(checks, "noExcessiveFooter", footer_hits <= MAX_FOOTER_HITS,
           f"footer_hits={footer_hits}")
    code_count = doc.get("codeBlockCount", 0)
    _check(checks, "codeBlocksPreserved",
           code_count == 0 or "```" in content,
           f"codeBlocks={code_count}")
    _check(checks, "headingLevelsReasonable", doc.get("headingCount", 0) >= 1,
           f"headings={doc.get('headingCount', 0)}")
    _check(checks, "markdownReadable",
           markdown_path.exists() and markdown_path.stat().st_size > 0,
           str(markdown_path.name))
    _check(checks, "jsonDeserializable", True, "loaded from json file")

    # 16. 术语替换日志：规范化正文的非代码文本中不应残留应替换的术语
    repl = doc.get("terminologyReplacements", {})
    non_code = _non_code_text(content)
    leftover = [term for term in term_map
                if term in non_code and repl.get(term, 0) == 0]
    for term in leftover:
        warnings.append(f"术语 '{term}' 在规范化正文的非代码文本中仍存在（未被替换）")
    _check(checks, "terminologyLogged", not leftover, f"replacements={repl}")

    passed = [c for c in checks if c["pass"]]
    failed = [c for c in checks if not c["pass"]]
    return {
        "documentKey": key,
        "title": doc.get("title", ""),
        "characterCount": doc.get("characterCount", len(content)),
        "passedChecks": len(passed),
        "failedChecks": len(failed),
        "checks": {c["name"]: {"pass": c["pass"], "detail": c["detail"]} for c in checks},
        "warnings": warnings,
        "pass": len(failed) == 0,
    }


def run(output_dir: Path | None = None, include_optional: bool = False) -> dict:
    """执行全部页面质量检查。"""
    base = Path(output_dir) if output_dir else DATASET_DIR
    src_path = SOURCES_PATH
    term_path = TERMINOLOGY_PATH
    if output_dir is not None:
        src_path = Path(output_dir) / "config" / "sources.json"
        term_path = Path(output_dir) / "config" / "terminology-map.json"
    with open(src_path, "r", encoding="utf-8") as f:
        sources_cfg = json.load(f)
    with open(term_path, "r", encoding="utf-8") as f:
        term_map = json.load(f).get("replacements", {})

    sources = [s for s in sources_cfg.get("sources", []) if s.get("enabled", True)]
    if not include_optional:
        sources = [s for s in sources if not s.get("optional", False)]
    source_by_key = {s["documentKey"]: s for s in sources}

    json_dir = base / "processed" / "json"
    md_dir = base / "processed" / "markdown"
    out_dir = base / "output"
    out_dir.mkdir(parents=True, exist_ok=True)

    doc_results = []
    seen_keys: dict[str, int] = {}
    hash_owner: dict[str, str] = {}
    for source in sources:
        key = source["documentKey"]
        json_file = json_dir / f"{key}.json"
        md_file = md_dir / f"{key}.md"
        if not json_file.exists():
            doc_results.append({
                "documentKey": key, "title": "", "characterCount": 0,
                "passedChecks": 0, "failedChecks": 1, "checks": {},
                "warnings": ["missing processed json"], "pass": False,
                "optional": source.get("optional", False),
            })
            continue
        with open(json_file, "r", encoding="utf-8") as f:
            doc = json.load(f)
        seen_keys[key] = seen_keys.get(key, 0) + 1
        if doc.get("contentHash"):
            prev = hash_owner.get(doc["contentHash"])
            if prev is not None and prev != key:
                doc_results.append({
                    "documentKey": key, "title": doc.get("title", ""),
                    "characterCount": doc.get("characterCount", 0),
                    "passedChecks": 0, "failedChecks": 0, "checks": {},
                    "warnings": [f"内容哈希与 {prev} 重复"], "pass": True,
                    "optional": source.get("optional", False),
                })
                continue
            hash_owner[doc["contentHash"]] = key
        meta_file = base / "raw" / "meta" / f"{key}.json"
        result = validate_document(doc, source, term_map, md_file, meta_file)
        result["optional"] = source.get("optional", False)
        doc_results.append(result)

    # 14. documentKey 唯一性
    dup_keys = {k for k, n in seen_keys.items() if n > 1}
    if dup_keys:
        for r in doc_results:
            if r["documentKey"] in dup_keys:
                r["warnings"].append("documentKey 重复")
                r["pass"] = False

    total = len(doc_results)
    failed_mandatory = [r for r in doc_results if not r["pass"] and not r.get("optional")]
    failed_optional = [r for r in doc_results if not r["pass"] and r.get("optional")]
    warnings_total = sum(len(r["warnings"]) for r in doc_results)

    overall_pass = len(failed_mandatory) == 0 and total > 0
    report = {
        "datasetName": sources_cfg.get("datasetName", ""),
        "datasetVersion": sources_cfg.get("datasetVersion", "v1"),
        "generatedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "totalDocuments": total,
        "passedDocuments": sum(1 for r in doc_results if r["pass"]),
        "failedMandatory": len(failed_mandatory),
        "failedOptional": len(failed_optional),
        "warningCount": warnings_total,
        "overallPass": overall_pass,
        "documents": doc_results,
    }
    with open(out_dir / "quality-report.json", "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    return report


def _console_summary(report: dict) -> str:
    lines = [
        "=== 数据质量检查摘要 ===",
        f"数据集: {report.get('datasetName')} {report.get('datasetVersion')}",
        f"文档总数: {report['totalDocuments']}",
        f"通过: {report['passedDocuments']}  必选失败: {report['failedMandatory']}  可选失败: {report['failedOptional']}  警告: {report['warningCount']}",
        f"总体: {'PASS' if report['overallPass'] else 'FAIL'}",
    ]
    for r in report["documents"]:
        marker = "OK " if r["pass"] else "FAIL"
        lines.append(f"  [{marker}] {r['documentKey']}: {r['passedChecks']}/{r['passedChecks'] + r['failedChecks']} 通过"
                     + (f"  警告: {r['warnings']}" if r["warnings"] else ""))
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="数据质量检查")
    parser.add_argument("--output-dir", type=Path, default=None)
    parser.add_argument("--include-optional", action="store_true")
    args = parser.parse_args(argv)
    report = run(output_dir=args.output_dir, include_optional=args.include_optional)
    print(_console_summary(report))
    return 0 if report["overallPass"] else 1


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    sys.exit(main())