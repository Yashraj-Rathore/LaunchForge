from __future__ import annotations

import copy
import json
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "eng"))

from release_manifest import ManifestError  # noqa: E402
from validate_supply_chain import (  # noqa: E402
    validate_event_worker_database_credentials,
    validate_java_runtime_images,
    validate_mockito_agent_configuration,
    validate_ruleset_contracts,
)


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


class FinalReviewCorrectionContractTest(unittest.TestCase):
    def test_committed_credentials_agent_and_runtime_satisfy_contracts(self) -> None:
        worker_config = (
            ROOT
            / "backend"
            / "launchforge-event-worker"
            / "src"
            / "main"
            / "resources"
            / "application.yml"
        ).read_text(encoding="utf-8")
        validate_event_worker_database_credentials(worker_config)
        validate_mockito_agent_configuration((ROOT / "pom.xml").read_text(encoding="utf-8"))
        validate_java_runtime_images(
            {
                path.name: path.read_text(encoding="utf-8")
                for path in (
                    ROOT / "deploy" / "docker" / "Dockerfile.java",
                    ROOT / "deploy" / "docker" / "Dockerfile.migrator",
                )
            }
        )

    def test_database_credential_default_is_rejected(self) -> None:
        unsafe = """
        username: ${LAUNCHFORGE_DB_USER:launchforge}
        password: ${LAUNCHFORGE_DB_PASSWORD:launchforge-local}
        """
        with self.assertRaisesRegex(ManifestError, "mandatory environment placeholders"):
            validate_event_worker_database_credentials(unsafe)

    def test_missing_failsafe_agent_is_rejected(self) -> None:
        pom_text = (ROOT / "pom.xml").read_text(encoding="utf-8")
        changed = pom_text.replace(
            "<argLine>@{argLine} -javaagent:${org.mockito:mockito-core:jar}</argLine>",
            "<argLine>@{argLine}</argLine>",
            1,
        )
        with self.assertRaisesRegex(ManifestError, "Surefire and Failsafe"):
            validate_mockito_agent_configuration(changed)

    def test_floating_runtime_tag_is_rejected(self) -> None:
        with self.assertRaisesRegex(ManifestError, r"Java 25.0.4\+7 manifest"):
            validate_java_runtime_images(
                {"Dockerfile.java": "ARG TEMURIN_JRE_IMAGE=eclipse-temurin:25-jre-noble"}
            )


if __name__ == "__main__":
    unittest.main()
