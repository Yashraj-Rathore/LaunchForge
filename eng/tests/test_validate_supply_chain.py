from __future__ import annotations

import copy
import json
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "eng"))

from release_manifest import ManifestError  # noqa: E402
from validate_supply_chain import validate_ruleset_contracts  # noqa: E402


class RulesetContractTest(unittest.TestCase):
    def setUp(self) -> None:
        rulesets = ROOT / ".github" / "rulesets"
        self.main = json.loads((rulesets / "main.json").read_text(encoding="utf-8"))
        self.release_tags = json.loads(
            (rulesets / "release-tags.json").read_text(encoding="utf-8")
        )

    def test_committed_rulesets_satisfy_contract(self) -> None:
        validate_ruleset_contracts(self.main, self.release_tags)

    def test_missing_required_check_is_rejected(self) -> None:
        changed = copy.deepcopy(self.main)
        status_rule = next(
            rule for rule in changed["rules"] if rule["type"] == "required_status_checks"
        )
        status_rule["parameters"]["required_status_checks"].pop()

        with self.assertRaisesRegex(ManifestError, "exactly match CI job names"):
            validate_ruleset_contracts(changed, self.release_tags)

    def test_tag_bypass_is_rejected(self) -> None:
        changed = copy.deepcopy(self.release_tags)
        changed["bypass_actors"] = [
            {"actor_id": 5, "actor_type": "RepositoryRole", "bypass_mode": "always"}
        ]

        with self.assertRaisesRegex(ManifestError, "immutable-tag contract"):
            validate_ruleset_contracts(self.main, changed)


if __name__ == "__main__":
    unittest.main()
