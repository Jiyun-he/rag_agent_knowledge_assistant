# -*- coding: utf-8 -*-
"""统一命令行入口。

用法示例：
    python scripts/mysql_docs/build_dataset.py --all
    python scripts/mysql_docs/build_dataset.py --download
    python scripts/mysql_docs/build_dataset.py --process --validate
    python scripts/mysql_docs/build_dataset.py --all --include-optional --force

退出码：
    0  全部成功（可选页面失败只产生警告）
    1  任一必选页面失败
    2  参数错误
重复运行幂等：下载阶段命中 SHA-256/304 自动跳过，处理与校验阶段覆盖写产物。
"""
from __future__ import annotations

import argparse
import json
import logging
import sys
from datetime import datetime, timezone
from pathlib import Path

import download
import preprocess
import validate

LOG = logging.getLogger("mysql_docs")

REPO_ROOT = Path(__file__).resolve().parents[2]
DATASET_DIR = REPO_ROOT / "dataset" / "mysql-innodb-zh"
SOURCES_PATH = DATASET_DIR / "config" / "sources.json"


def _optional_keys(include_optional: bool) -> set[str]:
    with open(SOURCES_PATH, "r", encoding="utf-8") as f:
        cfg = json.load(f)
    return {s["documentKey"] for s in cfg.get("sources", []) if s.get("optional")}


def _failed_keys(results: list[dict], optional: set[str]) -> tuple[list[str], list[str]]:
    mandatory = [r["documentKey"] for r in results
                 if r.get("status") == "failed" and r["documentKey"] not in optional]
    opt = [r["documentKey"] for r in results
           if r.get("status") == "failed" and r["documentKey"] in optional]
    return mandatory, opt


def _assemble_jsonl(output_dir: Path | None = None) -> int:
    """把 processed/json 下的每个 Document 汇总为 output/documents.jsonl。"""
    base = Path(output_dir) if output_dir else DATASET_DIR
    json_dir = base / "processed" / "json"
    out_dir = base / "output"
    out_dir.mkdir(parents=True, exist_ok=True)
    docs = []
    for f in sorted(json_dir.glob("*.json")):
        with open(f, "r", encoding="utf-8") as fh:
            docs.append(json.load(fh))
    with open(out_dir / "documents.jsonl", "w", encoding="utf-8") as fh:
        for d in docs:
            fh.write(json.dumps(d, ensure_ascii=False) + "\n")
    return len(docs)


def _write_manifest(output_dir: Path | None = None) -> dict:
    """生成 output/manifest.json，附带 quality-report 中的质量信息。"""
    base = Path(output_dir) if output_dir else DATASET_DIR
    out_dir = base / "output"
    out_dir.mkdir(parents=True, exist_ok=True)
    with open(SOURCES_PATH, "r", encoding="utf-8") as f:
        cfg = json.load(f)

    docs = []
    for f in sorted((base / "processed" / "json").glob("*.json")):
        with open(f, "r", encoding="utf-8") as fh:
            docs.append(json.load(fh))

    quality = {}
    q_path = out_dir / "quality-report.json"
    if q_path.exists():
        with open(q_path, "r", encoding="utf-8") as fh:
            qr = json.load(fh)
        quality = {d["documentKey"]: d for d in qr.get("documents", [])}

    entries = []
    for d in sorted(docs, key=lambda x: x["documentKey"]):
        key = d["documentKey"]
        q = quality.get(key, {})
        entries.append({
            "documentKey": key,
            "title": d.get("title", ""),
            "sourceUrl": d.get("sourceUrl", ""),
            "officialUrl": d.get("officialUrl", ""),
            "rawHtmlHash": d.get("rawHtmlHash", ""),
            "contentHash": d.get("contentHash", ""),
            "rawHtmlFile": f"raw/html/{key}.html",
            "processedMarkdownFile": f"processed/markdown/{key}.md",
            "processedJsonFile": f"processed/json/{key}.json",
            "characterCount": d.get("characterCount", 0),
            "headingCount": d.get("headingCount", 0),
            "codeBlockCount": d.get("codeBlockCount", 0),
            "tableCount": d.get("tableCount", 0),
            "passedQuality": bool(q.get("pass", False)),
            "warnings": q.get("warnings", []),
        })

    failed = [e for e in entries if not e["passedQuality"]]
    manifest = {
        "datasetName": cfg.get("datasetName", ""),
        "datasetVersion": cfg.get("datasetVersion", "v1"),
        "builtAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "pageTotal": len(entries),
        "successCount": len(entries) - len(failed),
        "failureCount": len(failed),
        "documents": entries,
    }
    with open(out_dir / "manifest.json", "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=2)
    return manifest


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="MySQL 8.0 InnoDB 中文文档数据集构建（下载 -> 处理 -> 校验）")
    parser.add_argument("--download", action="store_true", help="只执行下载")
    parser.add_argument("--process", action="store_true", help="只执行正文提取与标准化")
    parser.add_argument("--validate", action="store_true", help="只执行质量检查")
    parser.add_argument("--all", action="store_true", help="下载 + 处理 + 校验")
    parser.add_argument("--include-optional", action="store_true",
                        help="包含 2 个可选页面")
    parser.add_argument("--force", action="store_true", help="下载时忽略缓存强制重下")
    parser.add_argument("--output-dir", type=Path, default=None,
                        help="覆盖数据集根目录（默认 dataset/mysql-innodb-zh）")
    args = parser.parse_args(argv)

    do_download = args.download or args.all
    do_process = args.process or args.all
    do_validate = args.validate or args.all
    if not (do_download or do_process or do_validate):
        parser.print_help()
        return 2

    exit_code = 0
    optional = _optional_keys(args.include_optional)

    if do_download:
        result = download.run(
            output_dir=args.output_dir,
            include_optional=args.include_optional,
            force=args.force,
        )
        print(f"[download] {json.dumps(result['stats'], ensure_ascii=False)}")
        mandatory_failed, opt_failed = _failed_keys(result["results"], optional)
        if mandatory_failed:
            print(f"[download] 必选页面失败: {mandatory_failed}")
            exit_code = 1
        if opt_failed:
            print(f"[download] 可选页面失败(仅警告): {opt_failed}")

    if do_process:
        result = preprocess.run(
            output_dir=args.output_dir,
            include_optional=args.include_optional,
        )
        print(f"[process] {json.dumps(result['stats'], ensure_ascii=False)}")
        mandatory_failed, opt_failed = _failed_keys(result["results"], optional)
        if mandatory_failed:
            print(f"[process] 必选页面失败: {mandatory_failed}")
            exit_code = 1
        if opt_failed:
            print(f"[process] 可选页面失败(仅警告): {opt_failed}")
        n = _assemble_jsonl(args.output_dir)
        print(f"[assemble] documents.jsonl 共 {n} 行")

    if do_validate:
        report = validate.run(
            output_dir=args.output_dir,
            include_optional=args.include_optional,
        )
        print(validate._console_summary(report))
        _assemble_jsonl(args.output_dir)
        manifest = _write_manifest(args.output_dir)
        print(f"[manifest] {manifest['datasetName']} {manifest['datasetVersion']} "
              f"页面={manifest['pageTotal']} 成功={manifest['successCount']} "
              f"失败={manifest['failureCount']}")
        if report["failedMandatory"]:
            print(f"[validate] 必选页面未通过质量检查: "
                  f"{[r['documentKey'] for r in report['documents'] if not r['pass'] and not r.get('optional')]}")
            exit_code = 1
        if report["failedOptional"]:
            print(f"[validate] 可选页面未通过(仅警告): "
                  f"{[r['documentKey'] for r in report['documents'] if not r['pass'] and r.get('optional')]}")

    return exit_code


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    sys.exit(main())