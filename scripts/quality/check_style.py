#!/usr/bin/env python3
"""Lightweight repository style checks with no third-party dependencies."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MAX_LINE_LENGTH = 120
WILDCARD_IMPORT = re.compile(r"^import\s+[\w.]+\.\*;$")


def source_files() -> list[Path]:
    files = list((ROOT / "src").rglob("*.java"))
    files.extend((ROOT / "scripts").rglob("*.py"))
    return sorted(path for path in files if "__pycache__" not in path.parts)


def check_file(path: Path) -> list[str]:
    relative = path.relative_to(ROOT).as_posix()
    content = path.read_text(encoding="utf-8")
    issues: list[str] = []

    if content and not content.endswith("\n"):
        issues.append(f"{relative}: missing final newline")

    for line_number, line in enumerate(content.splitlines(), start=1):
        if line.rstrip(" \t") != line:
            issues.append(f"{relative}:{line_number}: trailing whitespace")

        indentation = line[: len(line) - len(line.lstrip())]
        if "\t" in indentation:
            issues.append(f"{relative}:{line_number}: tab used for indentation")

        if len(line) > MAX_LINE_LENGTH:
            issues.append(
                f"{relative}:{line_number}: line has {len(line)} characters "
                f"(maximum {MAX_LINE_LENGTH})"
            )

        if path.suffix == ".java" and WILDCARD_IMPORT.fullmatch(line):
            issues.append(f"{relative}:{line_number}: wildcard import is not allowed")

    return issues


def main() -> int:
    files = source_files()
    issues = [issue for path in files for issue in check_file(path)]

    if issues:
        print("Style check failed:")
        for issue in issues:
            print(f"  - {issue}")
        return 1

    print(f"Style check passed: {len(files)} source files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
