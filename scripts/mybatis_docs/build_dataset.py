# -*- coding: utf-8 -*-
"""MyBatis 中文文档数据集统一命令行入口。

用法：
    python scripts/mybatis_docs/build_dataset.py --all
    python scripts/mybatis_docs/build_dataset.py --fetch
    python scripts/mybatis_docs/build_dataset.py --process --validate
    python scripts/mybatis_docs/build_dataset.py --all --force

退出码：0 成功；1 任一必选 Document 失败；2 参数错误。
重复运行幂等：fetch 阶段 SHA-256 命中自动跳过，process/validate 覆盖写产物。
"""
from __future__ import annotations

import argparse
import json
import logging
import sys
from datetime import datetime, timezone
from pathlib import Path

import fetch_source
import preprocess
import validate

LOG = logging.getLogger("mybatis_docs")

REPO_ROOT = Path(__file__).resolve().parents[2]
DATASET_DIR = REPO_ROOT / "dataset" / "mybatis-3.5.19-zh"
SOURCES_PATH = DATASET_DIR / "config" / "sources.json"


def _failed_keys(results: list[dict]) -> list[str]:
    return [r["documentKey"] for r in results if r.get("status") == "failed"]


def _assemble_jsonl(output_dir: Path | None = None) -> int:
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
            "fetchedAt": d.get("fetchedAt", ""),
            "rawHtmlHash": d.get("rawHtmlHash", ""),
            "contentHash": d.get("contentHash", ""),
            "characterCount": d.get("characterCount", 0),
            "headingCount": d.get("headingCount", 0),
            "codeBlockCount": d.get("codeBlockCount", 0),
            "tableCount": d.get("tableCount", 0),
            "qualityStatus": "pass" if q.get("pass", False) else "fail",
        })

    failed = [e for e in entries if e["qualityStatus"] != "pass"]
    manifest = {
        "dataset": cfg.get("dataset", ""),
        "datasetVersion": cfg.get("datasetVersion", "v1"),
        "sourceProject": cfg.get("sourceProject", "mybatis/mybatis-3"),
        "sourceVersion": cfg.get("sourceVersion", "3.5.19"),
        "builtAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "documentCount": len(entries),
        "documents": entries,
    }
    with open(out_dir / "manifest.json", "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=2)
    return manifest


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="MyBatis 3.5.19 官方中文文档数据集构建（fetch -> process -> validate）")
    parser.add_argument("--fetch", action="store_true", help="下载 8 个官方中文页面")
    parser.add_argument("--process", action="store_true", help="提取正文并生成 Document")
    parser.add_argument("--validate", action="store_true", help="执行质量检查")
    parser.add_argument("--all", action="store_true", help="fetch + process + validate")
    parser.add_argument("--force", action="store_true", help="fetch 忽略缓存强制重下")
    parser.add_argument("--output-dir", type=Path, default=None)
    args = parser.parse_args(argv)

    do_fetch = args.fetch or args.all
    do_process = args.process or args.all
    do_validate = args.validate or args.all
    if not (do_fetch or do_process or do_validate):
        parser.print_help()
        return 2

    exit_code = 0
    if do_fetch:
        result = fetch_source.run(output_dir=args.output_dir, force=args.force)
        print(f"[fetch] {json.dumps(result['stats'], ensure_ascii=False)}")
        failed = _failed_keys(result["results"])
        if failed:
            print(f"[fetch] 失败页面: {failed}")
            exit_code = 1

    if do_process:
        result = preprocess.run(output_dir=args.output_dir)
        print(f"[process] {json.dumps(result['stats'], ensure_ascii=False)}")
        failed = _failed_keys(result["results"])
        if failed:
            print(f"[process] 失败页面: {failed}")
            exit_code = 1
        n = _assemble_jsonl(args.output_dir)
        print(f"[assemble] documents.jsonl 共 {n} 行")

    if do_validate:
        report = validate.run(output_dir=args.output_dir)
        print(validate._console_summary(report))
        _assemble_jsonl(args.output_dir)
        manifest = _write_manifest(args.output_dir)
        print(f"[manifest] {manifest['dataset']} {manifest['datasetVersion']} "
              f"documents={manifest['documentCount']}")
        if not report["overallPass"]:
            exit_code = 1

    return exit_code


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    sys.exit(main())