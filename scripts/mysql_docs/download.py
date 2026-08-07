# -*- coding: utf-8 -*-
"""MySQL 中文参考手册白名单页面下载器。

从 sources.json 读取白名单，逐个下载原始 HTML 到 raw/html/，并把响应元数据
（状态码、ETag、Last-Modified、响应时间、SHA-256）写入 raw/meta/。

- 已存在且内容未变化时自动跳过（条件请求 304 或 SHA-256 相同）。
- --force 强制重新下载。
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

LOG = logging.getLogger("mysql_docs")

REPO_ROOT = Path(__file__).resolve().parents[2]
DATASET_DIR = REPO_ROOT / "dataset" / "mysql-innodb-zh"
CONFIG_DIR = DATASET_DIR / "config"
SOURCES_PATH = CONFIG_DIR / "sources.json"
RAW_HTML_DIR = DATASET_DIR / "raw" / "html"
RAW_META_DIR = DATASET_DIR / "raw" / "meta"

DEFAULT_INTERVAL_SECONDS = 2.0
DEFAULT_TIMEOUT_SECONDS = 30
DEFAULT_USER_AGENT = "RAG-DatasetBuilder/1.0 (offline-eval-dataset; local build)"


def load_sources(include_optional: bool = False, output_dir: Path | None = None) -> list[dict]:
    """读取白名单；include_optional=False 时只返回核心页面（optional=false）。"""
    path = SOURCES_PATH
    if output_dir is not None:
        path = Path(output_dir) / "config" / "sources.json"
    with open(path, "r", encoding="utf-8") as f:
        config = json.load(f)
    enabled = [s for s in config.get("sources", []) if s.get("enabled", True)]
    if not include_optional:
        enabled = [s for s in enabled if not s.get("optional", False)]
    return enabled


def load_request_config(output_dir: Path | None = None) -> dict:
    path = SOURCES_PATH
    if output_dir is not None:
        path = Path(output_dir) / "config" / "sources.json"
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f).get("request", {})


def sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _meta_etag_last(meta_path: Path) -> tuple[str | None, str | None]:
    try:
        with open(meta_path, "r", encoding="utf-8") as f:
            meta = json.load(f)
        return meta.get("etag"), meta.get("lastModified")
    except (OSError, json.JSONDecodeError):
        return None, None


def download_one(source: dict, base: Path, force: bool,
                 timeout: float, user_agent: str,
                 retries: int, backoff: float) -> dict:
    """下载单个页面。base 为数据集根目录。返回状态记录。"""
    key = source["documentKey"]
    html_path = base / "raw" / "html" / f"{key}.html"
    meta_path = base / "raw" / "meta" / f"{key}.json"
    html_path.parent.mkdir(parents=True, exist_ok=True)
    meta_path.parent.mkdir(parents=True, exist_ok=True)

    url = source["sourceUrl"]
    headers = {
        "User-Agent": user_agent,
        "Accept": "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.5",
    }

    existing_sha = None
    if html_path.exists():
        existing_sha = sha256_hex(html_path.read_bytes())

    if not force and html_path.exists():
        etag, last_modified = _meta_etag_last(meta_path)
        if etag:
            headers["If-None-Match"] = etag
        if last_modified:
            headers["If-Modified-Since"] = last_modified

    last_error = None
    for attempt in range(retries + 1):
        started = time.perf_counter()
        try:
            resp = requests.get(url, headers=headers, timeout=timeout)
            elapsed_ms = int((time.perf_counter() - started) * 1000)

            if resp.status_code == 304:
                return {
                    "documentKey": key, "status": "skipped",
                    "reason": "unchanged (HTTP 304)",
                    "httpStatus": 304, "responseTimeMs": elapsed_ms,
                }

            resp.raise_for_status()

            raw = resp.content
            sha = sha256_hex(raw)

            if not force and html_path.exists() and sha == existing_sha:
                return {
                    "documentKey": key, "status": "skipped",
                    "reason": "unchanged (sha256 match)",
                    "httpStatus": resp.status_code, "responseTimeMs": elapsed_ms,
                    "sha256": sha,
                }

            html_path.write_bytes(raw)
            meta = {
                "documentKey": key,
                "url": url,
                "officialUrl": source.get("officialUrl", ""),
                "httpStatus": resp.status_code,
                "etag": resp.headers.get("ETag"),
                "lastModified": resp.headers.get("Last-Modified"),
                "responseTimeMs": elapsed_ms,
                "contentLength": len(raw),
                "fetchedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                "sha256": sha,
            }
            with open(meta_path, "w", encoding="utf-8") as f:
                json.dump(meta, f, ensure_ascii=False, indent=2)

            action = "updated" if existing_sha and existing_sha != sha else "downloaded"
            return {
                "documentKey": key, "status": action,
                "reason": "ok", "httpStatus": resp.status_code,
                "responseTimeMs": elapsed_ms, "sha256": sha, "size": len(raw),
            }
        except requests.exceptions.RequestException as exc:
            last_error = f"{type(exc).__name__}: {exc}"
            LOG.warning("[%s] attempt %d/%d failed: %s",
                        key, attempt + 1, retries + 1, last_error)
            if attempt < retries:
                time.sleep(backoff)
        except OSError as exc:
            last_error = f"IOError: {exc}"
            break

    return {"documentKey": key, "status": "failed", "reason": last_error or "unknown"}


def run(output_dir: Path | None = None, include_optional: bool = False,
        force: bool = False, interval: float | None = None,
        timeout: float | None = None, user_agent: str | None = None,
        retries: int | None = None, backoff: float | None = None) -> dict:
    """执行下载流程，返回统计结果。"""
    base = Path(output_dir) if output_dir else DATASET_DIR
    req = load_request_config(output_dir)
    interval = interval if interval is not None else req.get("requestIntervalSeconds", DEFAULT_INTERVAL_SECONDS)
    timeout = timeout if timeout is not None else req.get("timeoutSeconds", DEFAULT_TIMEOUT_SECONDS)
    retries = retries if retries is not None else req.get("retries", 2)
    backoff = backoff if backoff is not None else req.get("retryBackoffSeconds", 3)
    user_agent = user_agent or req.get("userAgent", DEFAULT_USER_AGENT)

    sources = load_sources(include_optional=include_optional, output_dir=output_dir)
    LOG.info("download: %d page(s) in whitelist (%s)",
             len(sources), "including optional" if include_optional else "core only")

    results = []
    for i, source in enumerate(sources):
        if i > 0:
            time.sleep(interval)
        record = download_one(source, base, force, timeout,
                              user_agent, retries, backoff)
        results.append(record)
        LOG.info("[%s] %s%s", record["documentKey"], record["status"],
                 f" ({record.get('reason')})" if record.get("reason") else "")

    stats = {"total": len(results), "downloaded": 0, "updated": 0,
             "skipped": 0, "failed": 0}
    for r in results:
        if r["status"] in ("downloaded", "updated"):
            stats["downloaded"] += 1
        elif r["status"] == "skipped":
            stats["skipped"] += 1
        elif r["status"] == "failed":
            stats["failed"] += 1
    return {"stats": stats, "results": results}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="下载 MySQL 中文参考手册白名单页面")
    parser.add_argument("--output-dir", type=Path, default=None,
                        help="覆盖数据集根目录（默认 dataset/mysql-innodb-zh）")
    parser.add_argument("--include-optional", action="store_true",
                        help="同时下载 2 个可选页面")
    parser.add_argument("--force", action="store_true",
                        help="忽略本地缓存强制重新下载")
    parser.add_argument("--interval", type=float, default=None,
                        help="请求间隔秒数")
    parser.add_argument("--timeout", type=float, default=None,
                        help="连接/读取超时秒数")
    args = parser.parse_args(argv)

    result = run(output_dir=args.output_dir, include_optional=args.include_optional,
                 force=args.force, interval=args.interval, timeout=args.timeout)
    print(json.dumps({"stats": result["stats"]}, ensure_ascii=False, indent=2))
    return 1 if result["stats"]["failed"] else 0


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    sys.exit(main())