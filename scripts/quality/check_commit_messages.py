#!/usr/bin/env python3
"""Validate new Git commit subjects against the repository convention."""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ZERO_SHA = "0" * 40
HEADER_PATTERN = re.compile(
    r"^(feat|fix|refactor|perf|test|docs|build|ci|chore|revert)"
    r"(\([a-z0-9][a-z0-9-]*\))?!?: [a-z0-9].+$"
)


def revision_from_event() -> str:
    event_path = os.getenv("GITHUB_EVENT_PATH")
    if not event_path:
        return "HEAD"

    event = json.loads(Path(event_path).read_text(encoding="utf-8"))
    pull_request = event.get("pull_request")
    if pull_request:
        return f"{pull_request['base']['sha']}..{pull_request['head']['sha']}"

    before = event.get("before")
    after = event.get("after") or os.getenv("GITHUB_SHA") or "HEAD"
    if before and before != ZERO_SHA:
        return f"{before}..{after}"
    return after


def commit_subjects(revision: str) -> list[tuple[str, str]]:
    result = subprocess.run(
        ["git", "log", "--no-merges", "--format=%H%x09%s", revision],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    commits: list[tuple[str, str]] = []
    for line in result.stdout.splitlines():
        commit_hash, subject = line.split("\t", maxsplit=1)
        commits.append((commit_hash, subject))
    return commits


def validation_error(subject: str) -> str | None:
    if len(subject) > 72:
        return f"subject has {len(subject)} characters (maximum 72)"
    if not subject.isascii():
        return "subject must use English ASCII characters"
    if not HEADER_PATTERN.fullmatch(subject):
        return "expected '<type>(<scope>): <lower-case summary>'"
    if subject.endswith("."):
        return "subject must not end with a period"
    return None


def main() -> int:
    requested_revision = sys.argv[1] if len(sys.argv) > 1 else revision_from_event()
    revision = requested_revision if ".." in requested_revision else f"{requested_revision}^!"
    commits = commit_subjects(revision)
    failures = [
        (commit_hash, subject, error)
        for commit_hash, subject in commits
        if (error := validation_error(subject))
    ]

    if failures:
        print(f"Commit message check failed for {revision}:")
        for commit_hash, subject, error in failures:
            print(f"  - {commit_hash[:8]} {subject!r}: {error}")
        return 1

    print(f"Commit message check passed: {len(commits)} commit(s) in {revision}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
