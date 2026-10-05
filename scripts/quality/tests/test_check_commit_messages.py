from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path
from unittest import mock


SCRIPT = Path(__file__).resolve().parents[1] / "check_commit_messages.py"
SPEC = importlib.util.spec_from_file_location("check_commit_messages", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
CHECKER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECKER)


class RevisionSelectionTest(unittest.TestCase):
    def revision_for(
        self,
        event: dict[str, object],
        *,
        base_available: bool = True,
        base_is_ancestor: bool = True,
    ) -> str:
        with (
            mock.patch.object(
                CHECKER,
                "commit_is_available",
                return_value=base_available,
            ),
            mock.patch.object(
                CHECKER,
                "is_ancestor",
                return_value=base_is_ancestor,
            ),
        ):
            return CHECKER.revision_from_event_data(event)

    def test_normal_push_checks_only_new_commits(self) -> None:
        event = {"before": "old", "after": "new"}

        self.assertEqual("old..new", self.revision_for(event))

    def test_force_push_with_missing_base_checks_reachable_history(self) -> None:
        event = {"before": "old", "after": "new", "forced": True}

        self.assertEqual(
            "new",
            self.revision_for(event, base_available=False),
        )

    def test_force_push_with_diverged_base_checks_reachable_history(self) -> None:
        event = {"before": "old", "after": "new", "forced": True}

        self.assertEqual(
            "new",
            self.revision_for(event, base_is_ancestor=False),
        )

    def test_new_branch_checks_reachable_history(self) -> None:
        event = {"before": CHECKER.ZERO_SHA, "after": "new", "created": True}

        self.assertEqual("new", self.revision_for(event))

    def test_pull_request_uses_base_and_head(self) -> None:
        event = {
            "pull_request": {
                "base": {"sha": "base"},
                "head": {"sha": "head"},
            }
        }

        self.assertEqual("base..head", self.revision_for(event))

    def test_explicit_commit_still_selects_one_commit(self) -> None:
        self.assertEqual("HEAD^!", CHECKER.command_line_revision("HEAD"))
        self.assertEqual(
            "base..head",
            CHECKER.command_line_revision("base..head"),
        )


if __name__ == "__main__":
    unittest.main()
