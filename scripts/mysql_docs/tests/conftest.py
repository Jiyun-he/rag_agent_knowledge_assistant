import json
import sys
from pathlib import Path

_SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

import pytest


@pytest.fixture
def sample_html() -> str:
    fixture = Path(__file__).resolve().parent / "fixtures" / "page.html"
    return fixture.read_text(encoding="utf-8")


@pytest.fixture
def sample_source() -> dict:
    return {
        "documentKey": "innodb-locking",
        "expectedTitle": "InnoDB 锁",
        "sourceVersion": "8.0",
        "language": "zh-CN",
        "sourceType": "community_translation",
        "sourceUrl": "https://mysql.net.cn/doc/refman/8.0/en/innodb-locking.html",
        "officialUrl": "https://dev.mysql.com/doc/refman/8.0/en/innodb-locking.html",
        "enabled": True,
        "optional": False,
    }


@pytest.fixture
def terminology() -> dict:
    from preprocess import TERMINOLOGY_PATH
    with open(TERMINOLOGY_PATH, "r", encoding="utf-8") as f:
        return json.load(f).get("replacements", {})