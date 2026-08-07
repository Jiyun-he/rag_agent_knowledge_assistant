# -*- coding: utf-8 -*-
"""MyBatis 官方中文文档白名单页面下载器。

从 config/sources.json 读取 8 个 mybatis.org/zh_CN 页面白名单，逐个下载
原始 HTML 到 raw/html/，响应元数据（状态码、SHA-256、fetchedAt、响应时间）
写入 raw/meta/。

- 已存在且内容未变化（SHA-256 相同）时自动跳过；
- --force 强制重新下载；
- 单页失败不中断其他页面。
"""
from __future__ import annotations

import argparse
import hashlib
import json
import logging
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

import requests

LOG = logging.getLogger("mybatis_docs")

REPO_ROOT = Path(__file__).resolve().parents[2]
DATASET_DIR = REPO_ROOT / "dataset" / "mybatis-3.5.19-zh"
CONFIG_DIR = DATASET_DIR / "config"
SOURCES_PATH = CONFIG_DIR / "sources.json"
RAW_HTML_DIR = DATASET_DIR / "raw" / "html"
RAW_META_DIR = DATASET_DIR / "raw" / "meta"

DEFAULT_TIMEOUT = 30
DEFAULT_USER_AGENT = "RAG-DatasetBuilder/1.0 (offline-eval-dataset; mybatis zh docs)"
DEFAULT_INTERVAL = 1.0


def load_sources(output_dir: Path | None = None) -> list[dict]:
    path = SOURCES_PATH
    if output_dir is not None:
        path = Path(output_dir) / "config" / "sources.json"
    with open(path, "r", encoding="utf-8") as f:
        config = json.load(f)
    return [s for s in config.get("sources", []) if s.get("enabled", True)]


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def fetch_one(source: dict, base: Path, force: bool, timeout: float,
              user_agent: str, retries: int, backoff: float) -> dict:
    key = source["documentKey"]
    html_path = base / "raw" / "html" / f"{key}.html"
    meta_path = base / "raw" / "meta" / f"{key}.json"
    html_path.parent.mkdir(parents=True, exist_ok=True)
    meta_path.parent.mkdir(parents=True, exist_ok=True)

    url = source["officialUrl"]
    headers = {"User-Agent": user_agent, "Accept-Language": "zh-CN,zh;q=0.9"}

    existing_sha = None
    if html_path.exists():
        existing_sha = _sha256(html_path.read_bytes())

    last_error = None
    for attempt in range(retries + 1):
        started = time.perf_counter()
        try:
            resp = requests.get(url, headers=headers, timeout=timeout)
            elapsed_ms = int((time.perf_counter() - started) * 1000)
            resp.raise_for_status()
            raw = resp.content
            sha = _sha256(raw)
            if not force and html_path.exists() and sha == existing_sha:
                return {"documentKey": key, "status": "skipped",
                        "reason": "unchanged (sha256 match)",
                        "httpStatus": resp.status_code, "responseTimeMs": elapsed_ms}
            html_path.write_bytes(raw)
            meta = {
                "documentKey": key,
                "url": url,
                "httpStatus": resp.status_code,
                "responseTimeMs": elapsed_ms,
                "contentLength": len(raw),
                "fetchedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                "sha256": sha,
            }
            with open(meta_path, "w", encoding="utf-8") as f:
                json.dump(meta, f, ensure_ascii=False, indent=2)
            action = "updated" if existing_sha and existing_sha != sha else "downloaded"
            return {"documentKey": key, "status": action, "reason": "ok",
                    "httpStatus": resp.status_code, "responseTimeMs": elapsed_ms,
                    "sha256": sha, "size": len(raw)}
        except requests.exceptions.RequestException as exc:
            last_error = f"{type(exc).__name__}: {exc}"
            LOG.warning("[%s] attempt %d/%d failed: %s", key, attempt + 1,
                        retries + 1, last_error)
            if attempt < retries:
                time.sleep(backoff)
        except OSError as exc:
            last_error = f"IOError: {exc}"
            break
    return {"documentKey": key, "status": "failed", "reason": last_error or "unknown"}


def run(output_dir: Path | None = None, force: bool = False,
        timeout: float | None = None, interval: float | None = None) -> dict:
    base = Path(output_dir) if output_dir else DATASET_DIR
    sources = load_sources(output_dir)
    LOG.info("fetch: %d page(s) in whitelist", len(sources))
    results = []
    for i, source in enumerate(sources):
        if i > 0:
            time.sleep(interval if interval is not None else DEFAULT_INTERVAL)
        record = fetch_one(source, base, force,
                           timeout if timeout is not None else DEFAULT_TIMEOUT,
                           DEFAULT_USER_AGENT, 2, 3.0)
        results.append(record)
        LOG.info("[%s] %s%s", record["documentKey"], record["status"],
                 f" ({record.get('reason')})" if record.get("reason") else "")
    stats = {"total": len(results), "downloaded": 0, "skipped": 0, "failed": 0}
    for r in results:
        if r["status"] == "skipped":
            stats["skipped"] += 1
        elif r["status"] == "failed":
            stats["failed"] += 1
        else:
            stats["downloaded"] += 1
    return {"stats": stats, "results": results}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="下载 MyBatis 官方中文文档白名单页面")
    parser.add_argument("--output-dir", type=Path, default=None)
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--timeout", type=float, default=None)
    parser.add_argument("--interval", type=float, default=None)
    args = parser.parse_args(argv)
    result = run(output_dir=args.output_dir, force=args.force,
                 timeout=args.timeout, interval=args.interval)
    print(json.dumps({"stats": result["stats"]}, ensure_ascii=False, indent=2))
    return 1 if result["stats"]["failed"] else 0


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    sys.exit(main())