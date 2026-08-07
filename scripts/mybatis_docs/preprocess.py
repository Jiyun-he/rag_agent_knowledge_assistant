# -*- coding: utf-8 -*-
"""MyBatis 官方中文文档正文提取与 Markdown 转换。

针对每个已下载的 raw/html/{key}.html：
1. 定位正文区域 <main>（回退 <article>），不使用整页 get_text()；
2. 提取标题（main 内第一个 h1）、段落、列表、表格、pre 代码块、行内 code、提示标签；
3. 删除导航/页眉/页脚（它们位于 main 之外，main 内过滤脚本与隐藏元素）；
4. 代码块按内容启发式标注语言（xml/java/sql），不修改正文；
5. 生成 processed/markdown/{key}.md（含 YAML frontmatter）与 processed/json/{key}.json。
"""
from __future__ import annotations

import argparse
import hashlib
import json
import logging
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

from bs4 import BeautifulSoup, NavigableString, Tag

LOG = logging.getLogger("mybatis_docs")

REPO_ROOT = Path(__file__).resolve().parents[2]
DATASET_DIR = REPO_ROOT / "dataset" / "mybatis-3.5.19-zh"
CONFIG_DIR = DATASET_DIR / "config"
SOURCES_PATH = CONFIG_DIR / "sources.json"
RAW_HTML_DIR = DATASET_DIR / "raw" / "html"
RAW_META_DIR = DATASET_DIR / "raw" / "meta"
PROCESSED_MARKDOWN_DIR = DATASET_DIR / "processed" / "markdown"
PROCESSED_JSON_DIR = DATASET_DIR / "processed" / "json"

SPACE_KEY = "mybatis-3.5.19-zh"
DATASET = "mybatis-3.5.19-zh"
SOURCE_VERSION = "3.5.19"

BLOCK_TAGS = {
    "h1", "h2", "h3", "h4", "h5", "h6",
    "p", "pre", "ul", "ol", "table", "blockquote",
    "div", "section", "article", "main", "dl", "dt", "dd",
    "figure", "hr",
}


def _normalize_ws(s: str) -> str:
    return re.sub(r"\s+", " ", s).strip()


def _normalize_code(s: str) -> str:
    s = s.replace("\r\n", "\n").replace("\r", "\n")
    lines = [ln.rstrip() for ln in s.split("\n")]
    while lines and not lines[0].strip():
        lines.pop(0)
    while lines and not lines[-1].strip():
        lines.pop()
    return "\n".join(lines)


_SQL_KW_RE = re.compile(
    r"(?i)^\s*(select|insert|update|delete|create|drop|alter|grant|merge|call|set)\b"
    r"|^\s*(order by|group by|where|from|join|having|union|values|into)\b")
_JAVA_HINT_RE = re.compile(
    r"^\s*(@|//|/\*|import |package |public |private |protected |class |"
    r"interface |enum |abstract |final |static |new |try |catch )"
    r"|SqlSessionFactory"
    r"|\bnew\s+\w+\s*\("
    r"|\.\w+\s*\(")


def _guess_lang(code: str) -> str:
    """按内容启发式推断代码块语言，仅用于围栏标注，不改变内容。"""
    stripped = code.lstrip()
    if stripped.startswith("<") or "DOCTYPE" in stripped[:200]:
        return "xml"
    if _SQL_KW_RE.search(stripped[:200]):
        return "sql"
    if _JAVA_HINT_RE.search(stripped[:300]):
        return "java"
    return ""


def _has_block_children(el: Tag) -> bool:
    return any(isinstance(c, Tag) and c.name in BLOCK_TAGS for c in el.children)


_HIDDEN_CLASS_RE = re.compile(r"\b(d-none|d-xl-none|d-lg-none|d-md-none|d-sm-none|sr-only|visually-hidden|hidden)\b")


def is_nav_node(tag: Tag) -> bool:
    """导航/模板类节点。main 之外由根选择排除；main 内过滤脚本、隐藏元素与导航。"""
    if tag.name in ("script", "style", "noscript", "form", "iframe", "template"):
        return True
    cls = " ".join(tag.get("class", []) or [])
    if _HIDDEN_CLASS_RE.search(cls):
        return True
    tag_id = (tag.get("id") or "").lower()
    if tag_id in ("toc", "breadcrumb", "search", "pagination", "related", "copyright"):
        return True
    return False


LABEL_HINT_RE = re.compile(r"\b(label|badge)\b")
ADMONITION_WORDS = ("note", "tip", "warning", "important", "notice", "信息")


def _render_label(el: Tag) -> str:
    text = _normalize_ws(el.get_text(" ", strip=True))
    return f"**{text}**" if text else ""


class RenderState:
    def __init__(self):
        self.heading_count = 0
        self.code_block_count = 0
        self.table_count = 0


def _inline_text(el: Tag, state: RenderState) -> str:
    parts = []
    for child in el.children:
        if isinstance(child, NavigableString):
            parts.append(str(child))
        elif isinstance(child, Tag):
            if child.name in ("script", "style"):
                continue
            if child.name == "br":
                parts.append(" ")
            elif child.name == "code":
                parts.append("`" + child.get_text() + "`")
            elif child.name == "img":
                parts.append(child.get("alt", ""))
            elif LABEL_HINT_RE.search(" ".join(child.get("class", []) or [])):
                parts.append(_render_label(child))
            else:
                parts.append(_inline_text(child, state))
    return _normalize_ws("".join(parts))


def _render_list(el: Tag, state: RenderState, out: list[str],
                 ordered: bool, depth: int = 0) -> None:
    idx = 1
    for li in el.find_all("li", recursive=False):
        prefix = f"{idx}. " if ordered else "- "
        text_parts = []
        sublists = []
        for child in li.children:
            if isinstance(child, NavigableString):
                if str(child).strip():
                    text_parts.append(_normalize_ws(str(child)))
            elif isinstance(child, Tag):
                if child.name in ("ul", "ol"):
                    sublists.append(child)
                else:
                    text_parts.append(_inline_text(child, state))
        text = " ".join(t for t in text_parts if t).strip()
        if text or sublists:
            out.append("  " * depth + prefix + text)
        for sub in sublists:
            _render_list(sub, state, out, sub.name == "ol", depth + 1)
        idx += 1


def _render_table(el: Tag, state: RenderState, out: list[str]) -> None:
    state.table_count += 1
    rows = []
    for tr in el.find_all("tr"):
        cells = []
        for cell in tr.find_all(["td", "th"]):
            t = _inline_text(cell, state).replace("|", "\\|").replace("\n", " ")
            cells.append(t)
        if cells:
            rows.append(cells)
    if not rows:
        return
    max_cols = max(len(r) for r in rows)
    for r in rows:
        while len(r) < max_cols:
            r.append("")
    header = rows[0]
    lines = ["| " + " | ".join(header) + " |",
             "| " + " | ".join("---" for _ in header) + " |"]
    for r in rows[1:]:
        lines.append("| " + " | ".join(r) + " |")
    out.append("\n".join(lines))


def _render_admonition(el: Tag, state: RenderState, out: list[str]) -> None:
    """MyBatis 官网提示标签为 <span class="label important">提示</span>。"""
    label = _render_label(el)
    body: list[str] = []
    for child in el.children:
        if isinstance(child, NavigableString):
            t = _normalize_ws(str(child))
            if t:
                body.append(t)
        elif isinstance(child, Tag):
            temp: list[str] = []
            render_block(child, state, temp)
            body.extend(temp)
    if not body and label:
        body = [label]
        label = ""
    lines: list[str] = []
    first = True
    for line in body:
        if line.strip():
            prefix = f"**{label}** " if (label and first) else ""
            lines.append("> " + prefix + line.strip())
            first = False
    if lines:
        out.append("\n".join(lines))


def render_block(el: Tag, state: RenderState, out: list[str]) -> None:
    name = el.name

    if name == "pre":
        code_el = el.find("code") or el
        text = _normalize_code(code_el.get_text())
        lang = _guess_lang(text)
        state.code_block_count += 1
        out.append(f"```{lang}\n{text}\n```")
        return

    if name in ("h1", "h2", "h3", "h4", "h5", "h6"):
        level = int(name[1])
        text = _inline_text(el, state)
        state.heading_count += 1
        out.append("#" * level + " " + text)
        return

    if name == "p":
        text = _inline_text(el, state)
        if text:
            out.append(text)
        return

    if name in ("ul", "ol"):
        _render_list(el, state, out, ordered=(name == "ol"))
        return

    if name == "table":
        _render_table(el, state, out)
        return

    if name == "blockquote":
        text = _inline_text(el, state)
        if text:
            out.append("> " + text)
        return

    if name == "hr":
        out.append("---")
        return

    # 提示标签（span.label important 等）按提示块处理
    cls = " ".join(el.get("class", []) or [])
    if LABEL_HINT_RE.search(cls) and any(w in cls for w in ("important", "note", "tip", "warning", "notice")):
        _render_admonition(el, state, out)
        return

    if _has_block_children(el):
        for child in el.children:
            if isinstance(child, Tag) and not is_nav_node(child):
                render_block(child, state, out)
    else:
        text = _inline_text(el, state)
        if text:
            out.append(text)


def _assemble_markdown(blocks: list[str]) -> str:
    text = "\n\n".join(b for b in blocks if b.strip())
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip() + "\n"


def _find_content_root(soup: BeautifulSoup) -> Tag:
    """定位正文区域。mybatis.org 官网正文在 <main>。"""
    for name in ("main", "article"):
        el = soup.find(name)
        if el is not None:
            return el
    section = soup.find("section")
    if section is not None:
        return section
    raise ValueError("无法定位正文区域（未找到 main/article/section）")


def _extract_title(root: Tag) -> str:
    """取第一个可见标题，跳过 Maven 模板的隐藏标题（如 d-none 的 "Avoid blank site"）。"""
    for name in ("h1", "h2", "h3"):
        for el in root.find_all(name):
            if is_nav_node(el):
                continue
            text = _normalize_ws(el.get_text(" ", strip=True))
            if text:
                return text
    return ""


def render_page(root: Tag) -> dict:
    state = RenderState()
    blocks: list[str] = []
    for child in root.children:
        if isinstance(child, Tag) and not is_nav_node(child):
            render_block(child, state, blocks)
    return {
        "markdown": _assemble_markdown(blocks),
        "headingCount": state.heading_count,
        "codeBlockCount": state.code_block_count,
        "tableCount": state.table_count,
    }


def _frontmatter(fields: dict) -> str:
    lines = ["---"]
    for k, v in fields.items():
        lines.append(f"{k}: {json.dumps(str(v), ensure_ascii=False)}")
    lines.append("---")
    return "\n".join(lines) + "\n"


def extract_document(html_text: str, source: dict, fetched_at: str,
                     raw_html_hash: str) -> dict:
    soup = BeautifulSoup(html_text, "html.parser")
    root = _find_content_root(soup)
    title = _extract_title(root)
    result = render_page(root)
    content = result["markdown"]
    if not content.strip():
        raise ValueError(f"页面正文为空：{source['documentKey']}")
    content_hash = hashlib.sha256(content.encode("utf-8")).hexdigest()
    return {
        "documentKey": source["documentKey"],
        "title": title,
        "spaceKey": SPACE_KEY,
        "dataset": DATASET,
        "sourceVersion": SOURCE_VERSION,
        "language": "zh-CN",
        "sourceType": "official_website",
        "sourceUrl": source.get("officialUrl", ""),
        "fetchedAt": fetched_at,
        "rawHtmlHash": raw_html_hash,
        "contentHash": content_hash,
        "headingCount": result["headingCount"],
        "codeBlockCount": result["codeBlockCount"],
        "tableCount": result["tableCount"],
        "characterCount": len(content),
        "content": content,
    }


def _markdown_file_text(source: dict, doc: dict) -> str:
    front = _frontmatter({
        "documentKey": doc["documentKey"],
        "title": doc["title"],
        "dataset": doc["dataset"],
        "spaceKey": doc["spaceKey"],
        "sourceVersion": doc["sourceVersion"],
        "language": doc["language"],
        "sourceType": doc["sourceType"],
        "sourceUrl": doc["sourceUrl"],
        "fetchedAt": doc["fetchedAt"],
        "contentHash": doc["contentHash"],
    })
    return front + doc["content"]


def process_page(source: dict, output_dir: Path | None = None) -> dict:
    base = Path(output_dir) if output_dir else DATASET_DIR
    key = source["documentKey"]
    html_path = base / "raw" / "html" / f"{key}.html"
    if not html_path.exists():
        raise FileNotFoundError(f"缺少原始 HTML：{html_path}")

    html_text = html_path.read_text(encoding="utf-8", errors="replace")
    raw_html_hash = hashlib.sha256(html_text.encode("utf-8")).hexdigest()

    fetched_at = datetime.now(timezone.utc).isoformat(timespec="seconds")
    meta_path = base / "raw" / "meta" / f"{key}.json"
    if meta_path.exists():
        try:
            with open(meta_path, "r", encoding="utf-8") as f:
                meta = json.load(f)
            fetched_at = meta.get("fetchedAt", fetched_at)
            raw_html_hash = meta.get("sha256", raw_html_hash)
        except (OSError, json.JSONDecodeError):
            pass

    doc = extract_document(html_text, source, fetched_at, raw_html_hash)

    md_dir = base / "processed" / "markdown"
    json_dir = base / "processed" / "json"
    md_dir.mkdir(parents=True, exist_ok=True)
    json_dir.mkdir(parents=True, exist_ok=True)

    (md_dir / f"{key}.md").write_text(_markdown_file_text(source, doc), encoding="utf-8")
    with open(json_dir / f"{key}.json", "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=2)

    return {"documentKey": key, "ok": True, "doc": doc}


def run(output_dir: Path | None = None) -> dict:
    base = Path(output_dir) if output_dir else DATASET_DIR
    src_path = SOURCES_PATH
    if output_dir is not None:
        src_path = Path(output_dir) / "config" / "sources.json"
    with open(src_path, "r", encoding="utf-8") as f:
        config = json.load(f)
    sources = [s for s in config.get("sources", []) if s.get("enabled", True)]

    results = []
    for source in sources:
        key = source["documentKey"]
        html_path = base / "raw" / "html" / f"{key}.html"
        if not html_path.exists():
            results.append({"documentKey": key, "status": "failed",
                            "reason": "missing raw html (run fetch first)"})
            continue
        try:
            r = process_page(source, output_dir=base)
            results.append({"documentKey": key, "status": "processed", "reason": "ok",
                            "characterCount": r["doc"]["characterCount"]})
        except Exception as exc:  # noqa: BLE001 单页失败不影响其他页面
            results.append({"documentKey": key, "status": "failed",
                            "reason": f"{type(exc).__name__}: {exc}"})

    stats = {"total": len(results), "processed": 0, "failed": 0}
    for r in results:
        if r["status"] == "processed":
            stats["processed"] += 1
        else:
            stats["failed"] += 1
    return {"stats": stats, "results": results}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="处理 MyBatis 官方中文文档原始 HTML")
    parser.add_argument("--output-dir", type=Path, default=None)
    args = parser.parse_args(argv)
    result = run(output_dir=args.output_dir)
    print(json.dumps({"stats": result["stats"]}, ensure_ascii=False, indent=2))
    return 1 if result["stats"]["failed"] else 0


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    sys.exit(main())