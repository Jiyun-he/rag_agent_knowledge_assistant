# -*- coding: utf-8 -*-
"""正文提取与标准化处理。

针对每个已下载的原始 HTML 页面：
1. 基于 DOM 定位正文区域（div.section / main / article），不使用整页 get_text()；
2. 提取标题、段落、列表、表格、pre/code、提示块，删除导航/页脚/脚本等无关元素；
3. 生成两份正文：
   - rawContent        仅格式清理，术语原样保留；
   - normalizedContent 按 terminology-map.json 做有限术语替换（不作用于代码块、不改 SQL 标识符）；
4. 输出 processed/markdown/{key}.md 与 processed/json/{key}.json。
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

LOG = logging.getLogger("mysql_docs")

REPO_ROOT = Path(__file__).resolve().parents[2]
DATASET_DIR = REPO_ROOT / "dataset" / "mysql-innodb-zh"
CONFIG_DIR = DATASET_DIR / "config"
SOURCES_PATH = CONFIG_DIR / "sources.json"
TERMINOLOGY_PATH = CONFIG_DIR / "terminology-map.json"
RAW_HTML_DIR = DATASET_DIR / "raw" / "html"
RAW_META_DIR = DATASET_DIR / "raw" / "meta"
PROCESSED_MARKDOWN_DIR = DATASET_DIR / "processed" / "markdown"
PROCESSED_JSON_DIR = DATASET_DIR / "processed" / "json"

SPACE_KEY = "mysql-8.0-innodb-transaction-locking-zh"

# 块级标签：用于判断一个容器是否直接包含块级子元素
BLOCK_TAGS = {
    "h1", "h2", "h3", "h4", "h5", "h6",
    "p", "pre", "ul", "ol", "table", "blockquote",
    "div", "section", "article", "main", "dl", "dt", "dd",
    "figure", "hr", "address", "header", "footer",
}

ADMONITION_KEYWORDS = ("note", "warning", "important", "caution", "tip", "notice", "admonition")


def _normalize_ws(s: str) -> str:
    """把连续空白（含换行）压缩为单个空格，并去除首尾空白。用于普通文本。"""
    return re.sub(r"\s+", " ", s).strip()


def _normalize_code_text(s: str) -> str:
    """规范代码块文本：统一换行、去行尾空白、去掉首尾空行，保留内部换行与缩进。"""
    s = s.replace("\r\n", "\n").replace("\r", "\n")
    lines = [ln.rstrip() for ln in s.split("\n")]
    while lines and not lines[0].strip():
        lines.pop(0)
    while lines and not lines[-1].strip():
        lines.pop()
    return "\n".join(lines)


def _code_lang(el: Tag) -> str:
    """从 pre/code 的 class 推断语言，如 language-sql -> sql。"""
    classes = el.get("class", []) or []
    for cls in classes:
        if cls.startswith("language-"):
            return cls[len("language-"):]
        if cls.startswith("lang-"):
            return cls[len("lang-"):]
    return ""


def _has_block_children(el: Tag) -> bool:
    return any(isinstance(c, Tag) and c.name in BLOCK_TAGS for c in el.children)


_NAV_CLASS_RE = re.compile(
    r"\b(toc|breadcrumb|search|pagination|menu|prev|next|related|copyright)\b")


def is_nav_node(tag: Tag) -> bool:
    """判断是否为导航/模板类节点，需要删除。

    注意使用词边界匹配：如 class 'copytoclipboard' 内含 'toc' 子串，
    但不是导航（'programlisting copytoclipboard' 是代码块 wrapper）。
    """
    if tag.name in ("nav", "header", "footer", "aside", "script", "style",
                    "noscript", "form", "iframe", "object", "template"):
        return True
    cls = " ".join(tag.get("class", []) or [])
    if _NAV_CLASS_RE.search(cls):
        return True
    tag_id = tag.get("id") or ""
    if tag_id in ("prev", "next", "related", "copyright", "top"):
        return True
    return False


def _is_admonition(cls: str) -> bool:
    return any(k in cls for k in ADMONITION_KEYWORDS)


class RenderState:
    """跨遍历共享的统计与替换记录。"""

    def __init__(self, term_map: dict, level_shift: int = 0):
        self.term_map = term_map
        self.level_shift = level_shift
        self.heading_count = 0
        self.code_block_count = 0
        self.table_count = 0
        self.replacements: dict[str, int] = {}


def _apply_terms(text: str, state: RenderState) -> str:
    for src, dst in state.term_map.items():
        n = text.count(src)
        if n:
            text = text.replace(src, dst)
            state.replacements[src] = state.replacements.get(src, 0) + n
    return text


def _inline_text(el: Tag, state: RenderState) -> str:
    """收集元素的行内可见文本。code 内容原样保留（不替换术语）；链接保留可见文字。"""
    parts = []
    for child in el.children:
        if isinstance(child, NavigableString):
            parts.append(_apply_terms(str(child), state))
        elif isinstance(child, Tag):
            if child.name in ("script", "style"):
                continue
            if child.name == "br":
                parts.append(" ")
            elif child.name == "code":
                # 行内代码保留原样，并用反引号标记；术语规范不作用于 code
                parts.append("`" + child.get_text() + "`")
            elif child.name == "img":
                parts.append(child.get("alt", ""))
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
                    text_parts.append(_apply_terms(_normalize_ws(str(child)), state))
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
    # 表格作为单个块输出，行之间用单个换行，保证 Markdown 表格结构成立
    out.append("\n".join(lines))


def _render_admonition(el: Tag, state: RenderState, out: list[str]) -> None:
    # 兼容两类提示块标题：admonition-title 或 p.title（镜像常用 div.note > p.title）
    title_el = el.find(class_="admonition-title")
    if title_el is None:
        for c in el.children:
            if isinstance(c, Tag) and "title" in " ".join(c.get("class", []) or []):
                title_el = c
                break
    label = _inline_text(title_el, state) if title_el else ""
    body: list[str] = []
    for child in el.children:
        if isinstance(child, NavigableString):
            t = _normalize_ws(str(child))
            if t:
                body.append(t)
        elif isinstance(child, Tag):
            if title_el is not None and child is title_el:
                continue
            if "admonition-title" in " ".join(child.get("class", []) or []):
                continue
            temp: list[str] = []
            render_block(child, state, temp)
            body.extend(temp)

    if not body and label:
        body = [label]
        label = ""
    # 提示块作为单个块输出（连续 > 行），不拆成多个空行分隔的 block
    lines: list[str] = []
    first = True
    for line in body:
        if line.strip():
            prefix = f"**{label}：** " if (label and first) else ""
            lines.append("> " + prefix + line.strip())
            first = False
    if lines:
        out.append("\n".join(lines))


def render_block(el: Tag, state: RenderState, out: list[str]) -> None:
    name = el.name

    if name == "pre":
        code_el = el.find("code") or el
        text = _normalize_code_text(code_el.get_text())
        lang = _code_lang(code_el)
        state.code_block_count += 1
        out.append(f"```{lang}\n{text}\n```")
        return

    if name in ("h1", "h2", "h3", "h4", "h5", "h6"):
        level = max(1, int(name[1]) - state.level_shift)
        text = _inline_text(el, state)
        state.heading_count += 1
        out.append("#" * level + " " + text)
        return

    if name == "p":
        cls = " ".join(el.get("class", []) or [])
        if _is_admonition(cls):
            _render_admonition(el, state, out)
        else:
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

    # 提示块（div.admonition / p.note 等）：class 命中关键词时按提示块渲染
    if " ".join(el.get("class", []) or []).strip() and _is_admonition(
            " ".join(el.get("class", []) or [])):
        _render_admonition(el, state, out)
        return

    # 容器类：有块级子元素则递归，否则按行内文本输出
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
    """定位正文区域。libmysql 页面正文为 div.section。"""
    section = soup.find("div", class_="section")
    if section is not None:
        return section
    main_el = soup.find("main")
    if main_el is not None:
        return main_el
    article = soup.find("article")
    if article is not None:
        return article
    raise ValueError("无法定位正文区域（未找到 div.section / main / article）")


_SECTION_NUM_RE = re.compile(r"^\d+(\.\d+)*\s+")


def _strip_section_number(title: str) -> str:
    """去掉标题前导的章节号，如 '15.7.1 InnoDB 锁定' -> 'InnoDB 锁定'。"""
    return _SECTION_NUM_RE.sub("", title).strip()


def _find_title_heading(root: Tag) -> Tag | None:
    """定位页面主标题元素（titlepage 内的标题，或正文第一个标题）。"""
    title_el = root.find(class_="title")
    if title_el is not None and isinstance(title_el, Tag):
        return title_el
    for h in ("h1", "h2", "h3", "h4", "h5", "h6"):
        el = root.find(h)
        if el is not None:
            return el
    return None


def _extract_title(root: Tag) -> str:
    h1 = root.find("h1")
    if h1 is not None:
        return _strip_section_number(_normalize_ws(h1.get_text(" ", strip=True)))
    title_el = root.find(class_="title")
    if title_el is not None:
        return _strip_section_number(_normalize_ws(title_el.get_text(" ", strip=True)))
    return ""


def _frontmatter(fields: dict) -> str:
    lines = ["---"]
    for k, v in fields.items():
        lines.append(f"{k}: {json.dumps(str(v), ensure_ascii=False)}")
    lines.append("---")
    return "\n".join(lines) + "\n"


def extract_document(html_text: str, source: dict, terminology: dict,
                     fetched_at: str, raw_html_hash: str) -> dict:
    """从单个 HTML 页面提取标准化 Document。"""
    soup = BeautifulSoup(html_text, "html.parser")
    root = _find_content_root(soup)
    title = _extract_title(root)

    title_heading = _find_title_heading(root)
    base_level = int(title_heading.name[1]) if title_heading is not None else 1

    raw_result = render_page(root, {}, base_level=base_level)
    norm_result = render_page(root, terminology, base_level=base_level)

    content = norm_result["markdown"]
    if not content.strip():
        raise ValueError(f"页面正文为空：{source['documentKey']}")

    # 标题也做有限术语规范（如镜像标题"虚线" -> "幻影行"）
    title_state = RenderState(terminology)
    title = _apply_terms(title, title_state)

    content_hash = hashlib.sha256(content.encode("utf-8")).hexdigest()
    replacements = dict(norm_result["replacements"])
    for term, count in title_state.replacements.items():
        replacements[term] = replacements.get(term, 0) + count
    replacements = {k: v for k, v in replacements.items() if v > 0}

    doc = {
        "documentKey": source["documentKey"],
        "title": title,
        "spaceKey": SPACE_KEY,
        "sourceVersion": source.get("sourceVersion", "8.0"),
        "language": source.get("language", "zh-CN"),
        "sourceType": source.get("sourceType", "community_translation"),
        "sourceUrl": source.get("sourceUrl", ""),
        "officialUrl": source.get("officialUrl", ""),
        "fetchedAt": fetched_at,
        "rawHtmlHash": raw_html_hash,
        "contentHash": content_hash,
        "headingCount": norm_result["headingCount"],
        "codeBlockCount": norm_result["codeBlockCount"],
        "tableCount": norm_result["tableCount"],
        "characterCount": len(content),
        "rawContent": raw_result["markdown"],
        "content": content,
    }
    if replacements:
        doc["terminologyReplacements"] = replacements
    return doc


def render_page(root: Tag, term_map: dict, base_level: int = 1) -> dict:
    """把正文 DOM 渲染为 markdown，返回正文与统计。

    base_level 为页面主标题的 HTML 标题级别（如镜像站点用 h3 作为主标题），
    渲染时整体下移使主标题落在 '#'，保留相对层级。默认 1（主标题即 h1）。
    """
    state = RenderState(term_map, level_shift=base_level - 1)
    blocks: list[str] = []
    for child in root.children:
        if isinstance(child, Tag) and not is_nav_node(child):
            render_block(child, state, blocks)
    return {
        "markdown": _assemble_markdown(blocks),
        "headingCount": state.heading_count,
        "codeBlockCount": state.code_block_count,
        "tableCount": state.table_count,
        "replacements": state.replacements,
    }


def _markdown_file_text(source: dict, doc: dict) -> str:
    front = _frontmatter({
        "documentKey": doc["documentKey"],
        "title": doc["title"],
        "sourceVersion": doc["sourceVersion"],
        "language": doc["language"],
        "sourceUrl": doc["sourceUrl"],
        "officialUrl": doc["officialUrl"],
        "fetchedAt": doc["fetchedAt"],
        "contentHash": doc["contentHash"],
    })
    return front + doc["content"]


def process_page(source: dict, terminology: dict, output_dir: Path | None = None) -> dict:
    """处理单个页面：读取 raw html + meta，输出 markdown 与 json。"""
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

    doc = extract_document(html_text, source, terminology, fetched_at, raw_html_hash)

    md_dir = base / "processed" / "markdown"
    json_dir = base / "processed" / "json"
    md_dir.mkdir(parents=True, exist_ok=True)
    json_dir.mkdir(parents=True, exist_ok=True)

    (md_dir / f"{key}.md").write_text(_markdown_file_text(source, doc), encoding="utf-8")
    with open(json_dir / f"{key}.json", "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=2)

    return {"documentKey": key, "ok": True, "doc": doc}


def run(output_dir: Path | None = None, include_optional: bool = False) -> dict:
    """处理所有已下载页面，返回统计。"""
    base = Path(output_dir) if output_dir else DATASET_DIR
    term_path = TERMINOLOGY_PATH
    src_path = SOURCES_PATH
    if output_dir is not None:
        term_path = Path(output_dir) / "config" / "terminology-map.json"
        src_path = Path(output_dir) / "config" / "sources.json"

    with open(term_path, "r", encoding="utf-8") as f:
        terminology = json.load(f).get("replacements", {})
    with open(src_path, "r", encoding="utf-8") as f:
        config = json.load(f)
    sources = [s for s in config.get("sources", []) if s.get("enabled", True)]
    if not include_optional:
        sources = [s for s in sources if not s.get("optional", False)]

    results = []
    for source in sources:
        key = source["documentKey"]
        html_path = base / "raw" / "html" / f"{key}.html"
        if not html_path.exists():
            results.append({"documentKey": key, "status": "failed",
                            "reason": "missing raw html (run download first)"})
            continue
        try:
            r = process_page(source, terminology, output_dir=base)
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
    parser = argparse.ArgumentParser(description="处理 MySQL 中文参考手册原始 HTML")
    parser.add_argument("--output-dir", type=Path, default=None)
    parser.add_argument("--include-optional", action="store_true")
    args = parser.parse_args(argv)
    result = run(output_dir=args.output_dir, include_optional=args.include_optional)
    print(json.dumps({"stats": result["stats"]}, ensure_ascii=False, indent=2))
    return 1 if result["stats"]["failed"] else 0


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    sys.exit(main())