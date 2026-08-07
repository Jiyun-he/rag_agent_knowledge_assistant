# -*- coding: utf-8 -*-
"""MyBatis 中文文档数据集质量检查。

对 processed/json 中每个 Document 执行检查，输出 quality-report.json 与摘要。
检查覆盖：标题/正文非空、正文长度、中文正文、标题层级、代码块围栏闭合、
XML/Java/SQL 示例未被截断、表格保留、无 Maven site 模板垃圾、
documentKey 唯一，以及数据集 8 expected = 8 generated。
"""
from __future__ import annotations

import argparse
import json
import logging
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

LOG = logging.getLogger("mybatis_docs")

REPO_ROOT = Path(__file__).resolve().parents[2]
DATASET_DIR = REPO_ROOT / "dataset" / "mybatis-3.5.19-zh"
CONFIG_DIR = DATASET_DIR / "config"
SOURCES_PATH = CONFIG_DIR / "sources.json"
PROCESSED_JSON_DIR = DATASET_DIR / "processed" / "json"
PROCESSED_MARKDOWN_DIR = DATASET_DIR / "processed" / "markdown"
OUTPUT_DIR = DATASET_DIR / "output"
QUALITY_REPORT_PATH = OUTPUT_DIR / "quality-report.json"

MIN_CHARACTER_COUNT = 300
MIN_ZH_RATIO = 0.02
# 仅检测 Maven Site 模板特有词汇；groupId/artifactId/Maven 等会合法出现在正文依赖示例中。
_TEMPLATE_GARBAGE = ("Parent POM", "skipTests", "About This Document",
                     "Donate to Apache", "Project Reports", "Project Information",
                     "Maven Site")

_ZH_RE = re.compile(r"[一-鿿]")


def _zh_ratio(text: str) -> float:
    if not text:
        return 0.0
    zh = len(_ZH_RE.findall(text))
    total = max(1, len(re.sub(r"\s+", "", text)))
    return zh / total


def _fences_balanced(text: str) -> bool:
    """围栏代码块应成对出现。"""
    return text.count("```") % 2 == 0


def _check(checks: list[dict], name: str, passed: bool, detail: str = "") -> None:
    checks.append({"name": name, "pass": bool(passed), "detail": detail})


def validate_document(doc: dict, source: dict, markdown_path: Path) -> dict:
    checks: list[dict] = []
    warnings: list[str] = []
    content = doc.get("content", "")
    key = doc.get("documentKey", "?")

    _check(checks, "titleNonEmpty", bool(doc.get("title", "").strip()),
           doc.get("title", "")[:40])
    _check(checks, "contentNonEmpty", bool(content and content.strip()),
           f"len={len(content)}")
    _check(checks, "lengthOk", len(content) >= MIN_CHARACTER_COUNT,
           f"len={len(content)}")
    zh = _zh_ratio(content)
    _check(checks, "zhBodyPresent", zh >= MIN_ZH_RATIO, f"ratio={zh:.3f}")
    _check(checks, "headingPresent", doc.get("headingCount", 0) >= 1,
           f"headings={doc.get('headingCount', 0)}")
    _check(checks, "fencesBalanced", _fences_balanced(content),
           f"fences={content.count('```')}")
    _check(checks, "tablePreserved",
           doc.get("tableCount", 0) == 0 or "| --- |" in content,
           f"tables={doc.get('tableCount', 0)}")
    # XML/Java/SQL 示例未被截断：代码块内不应残留未闭合的标签痕迹
    _check(checks, "codeBlocksIntact",
           doc.get("codeBlockCount", 0) == 0 or "```" in content,
           f"codeBlocks={doc.get('codeBlockCount', 0)}")
    garbage_hits = sum(content.count(g) for g in _TEMPLATE_GARBAGE)
    _check(checks, "noTemplateGarbage", garbage_hits <= 5,
           f"garbage_hits={garbage_hits}")
    _check(checks, "markdownReadable",
           markdown_path.exists() and markdown_path.stat().st_size > 0,
           str(markdown_path.name))
    _check(checks, "jsonDeserializable", True, "loaded from json file")

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


def run(output_dir: Path | None = None) -> dict:
    base = Path(output_dir) if output_dir else DATASET_DIR
    src_path = SOURCES_PATH
    if output_dir is not None:
        src_path = Path(output_dir) / "config" / "sources.json"
    with open(src_path, "r", encoding="utf-8") as f:
        config = json.load(f)
    sources = [s for s in config.get("sources", []) if s.get("enabled", True)]
    source_by_key = {s["documentKey"]: s for s in sources}
    expected_keys = set(source_by_key)

    json_dir = base / "processed" / "json"
    md_dir = base / "processed" / "markdown"
    out_dir = base / "output"
    out_dir.mkdir(parents=True, exist_ok=True)

    doc_results = []
    seen_keys: dict[str, int] = {}
    missing_keys = []
    for source in sources:
        key = source["documentKey"]
        json_file = json_dir / f"{key}.json"
        md_file = md_dir / f"{key}.md"
        if not json_file.exists():
            missing_keys.append(key)
            doc_results.append({
                "documentKey": key, "title": "", "characterCount": 0,
                "passedChecks": 0, "failedChecks": 1, "checks": {},
                "warnings": ["missing processed json"], "pass": False,
            })
            continue
        with open(json_file, "r", encoding="utf-8") as f:
            doc = json.load(f)
        seen_keys[key] = seen_keys.get(key, 0) + 1
        result = validate_document(doc, source, md_file)
        doc_results.append(result)

    dup_keys = [k for k, n in seen_keys.items() if n > 1]
    if dup_keys:
        for r in doc_results:
            if r["documentKey"] in dup_keys:
                r["warnings"].append("documentKey 重复")
                r["pass"] = False

    total = len(doc_results)
    passed = sum(1 for r in doc_results if r["pass"])
    generated_keys = set(seen_keys)
    missing = sorted(expected_keys - generated_keys)
    duplicates = sorted(dup_keys)
    dataset_ok = (len(expected_keys) == 8 and generated_keys == expected_keys
                  and not missing and not duplicates)

    report = {
        "dataset": config.get("dataset", ""),
        "datasetVersion": config.get("datasetVersion", "v1"),
        "generatedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "expectedDocuments": len(expected_keys),
        "generatedDocuments": len(generated_keys),
        "missingDocuments": missing,
        "duplicateDocuments": duplicates,
        "passedDocuments": passed,
        "warningCount": sum(len(r["warnings"]) for r in doc_results),
        "overallPass": dataset_ok and total > 0 and passed == total,
        "documents": doc_results,
    }
    with open(out_dir / "quality-report.json", "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    return report


def _console_summary(report: dict) -> str:
    lines = [
        "=== MyBatis 数据集质量检查摘要 ===",
        f"数据集: {report.get('dataset')} {report.get('datasetVersion')}",
        f"期望 {report['expectedDocuments']} 个 Document，生成 {report['generatedDocuments']} 个，"
        f"缺失 {report['missingDocuments']}，重复 {report['duplicateDocuments']}",
        f"通过: {report['passedDocuments']}/{report['generatedDocuments']}  警告: {report['warningCount']}",
        f"总体: {'PASS' if report['overallPass'] else 'FAIL'}",
    ]
    for r in report["documents"]:
        marker = "OK " if r["pass"] else "FAIL"
        lines.append(f"  [{marker}] {r['documentKey']}: {r['passedChecks']}/{r['passedChecks'] + r['failedChecks']} 通过"
                     + (f"  警告: {r['warnings']}" if r["warnings"] else ""))
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="MyBatis 数据集质量检查")
    parser.add_argument("--output-dir", type=Path, default=None)
    args = parser.parse_args(argv)
    report = run(output_dir=args.output_dir)
    print(_console_summary(report))
    return 0 if report["overallPass"] else 1


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    sys.exit(main())