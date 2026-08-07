import json
import sys
from pathlib import Path

_SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

import pytest


@pytest.fixture
def sample_html() -> str:
    fixture = Path(__file__).resolve().parent / "fixtures" / "dynamic-sql.html"
    return fixture.read_text(encoding="utf-8")


@pytest.fixture
def sample_source() -> dict:
    return {
        "documentKey": "dynamic-sql",
        "expectedTitle": "动态 SQL",
        "officialUrl": "https://mybatis.org/mybatis-3/zh_CN/dynamic-sql.html",
        "enabled": True,
    }