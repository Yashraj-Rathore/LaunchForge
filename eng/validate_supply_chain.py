#!/usr/bin/env python3
"""Validate workflow pinning and release-policy repository contracts."""

from __future__ import annotations

import json
import re
import sys
from datetime import date
from pathlib import Path

from release_manifest import ManifestError, validate_compatibility


ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = ROOT / ".github" / "workflows"
RULESETS = ROOT / ".github" / "rulesets"
ACTION_PATTERN = re.compile(r"^\s*-?\s*uses:\s*([^@\s]+)@([^\s#]+)(?:\s+#\s*(\S+))?", re.MULTILINE)
SHA_PATTERN = re.compile(r"[0-9a-f]{40}\Z")
MIGRATION_PATTERN = re.compile(r"V([1-9][0-9]*)__[^/\\]+\.sql\Z")
REQUIRED_WORKFLOWS = {
    "ci.yml",
    "release.yml",
    "promote-production.yml",
    "rollback-production.yml",
}
REQUIRED_CHECKS = (
    "Dependency, secret, and repository security",
    "Java and JavaScript evaluator compatibility",
    "Java quality and integration",
    "Frontend quality",
    "OIDC tenancy browser smoke",
    "Repository contracts",
)


def workflow_paths() -> list[Path]:
    return sorted((*WORKFLOWS.glob("*.yml"), *WORKFLOWS.glob("*.yaml")))
REQUIRED_RELEASE_TOKENS = {
    "release.yml": (
        "environment: staging",
        "attest-build-provenance",
        "attest-sbom",
        "trivy-action",
        "release_manifest.py assemble",
        "component=database-schema",
        "Run staged OIDC, publish, Edge, SSE, and Java demo smoke",
    ),
    "promote-production.yml": (
        "environment: production",
        "release_manifest.py assert-compatible",
        "release_manifest.py render-values",
        "component=database-schema",
        "migrations-enabled true",
    ),
    "rollback-production.yml": (
        "environment: production",
        "release_manifest.py assert-compatible",
        "--mode rollback",
        "component=database-schema",
        "migrations-enabled false",
    ),
}


def fail(message: str) -> None:
    raise ManifestError(message)


def validate_action_pins() -> None:
    for path in workflow_paths():
        text = path.read_text(encoding="utf-8")
        if "permissions: write-all" in text:
            fail(f"{path.relative_to(ROOT)} must not use write-all permissions")
        for action, reference, version_comment in ACTION_PATTERN.findall(text):
            if action.startswith("./"):
                continue
            if not SHA_PATTERN.fullmatch(reference):
                fail(f"{path.relative_to(ROOT)} uses unpinned action {action}@{reference}")
            if not version_comment or not version_comment.startswith("v"):
                fail(f"{path.relative_to(ROOT)} must document the version beside {action}@{reference}")


def validate_workflow_contracts() -> None:
    present = {path.name for path in workflow_paths()}
    missing = REQUIRED_WORKFLOWS - present
    if missing:
        fail(f"Missing required workflows: {sorted(missing)}")
    for name, tokens in REQUIRED_RELEASE_TOKENS.items():
        text = (WORKFLOWS / name).read_text(encoding="utf-8")
        for token in tokens:
            if token not in text:
                fail(f"{name} is missing release contract token: {token}")


def validate_ruleset_contracts(main: dict, release_tags: dict) -> None:
    expected_main_envelope = {
        "name": "Protect main",
        "target": "branch",
        "enforcement": "active",
        "bypass_actors": [],
        "conditions": {
            "ref_name": {"include": ["refs/heads/main"], "exclude": []}
        },
    }
    for key, expected in expected_main_envelope.items():
        if main.get(key) != expected:
            fail(f"main ruleset {key} must be {expected!r}")

    main_rules = main.get("rules")
    if not isinstance(main_rules, list):
        fail("main ruleset rules must be an array")
    by_type = {rule.get("type"): rule for rule in main_rules if isinstance(rule, dict)}
    expected_types = {
        "deletion",
        "non_fast_forward",
        "required_linear_history",
        "pull_request",
        "required_status_checks",
    }
    if set(by_type) != expected_types or len(main_rules) != len(expected_types):
        fail(f"main ruleset must contain exactly {sorted(expected_types)}")

    expected_pull_request = {
        "allowed_merge_methods": ["squash", "rebase"],
        "dismiss_stale_reviews_on_push": True,
        "require_code_owner_review": True,
        "require_last_push_approval": False,
        "required_approving_review_count": 1,
        "required_review_thread_resolution": True,
    }
    if by_type["pull_request"].get("parameters") != expected_pull_request:
        fail("main ruleset pull-request protections do not match the release contract")

    status_parameters = by_type["required_status_checks"].get("parameters")
    if not isinstance(status_parameters, dict):
        fail("main ruleset status-check parameters must be an object")
    contexts = status_parameters.get("required_status_checks")
    if not isinstance(contexts, list) or contexts != [
        {"context": context} for context in REQUIRED_CHECKS
    ]:
        fail("main ruleset required checks must exactly match CI job names")
    if status_parameters.get("strict_required_status_checks_policy") is not True:
        fail("main ruleset must require the pull-request head to be current")
    if status_parameters.get("do_not_enforce_on_create") is not False:
        fail("main ruleset must enforce required checks on branch creation")

    expected_release_tags = {
        "name": "Protect release tags",
        "target": "tag",
        "enforcement": "active",
        "bypass_actors": [],
        "conditions": {
            "ref_name": {"include": ["refs/tags/v*"], "exclude": []}
        },
        "rules": [{"type": "deletion"}, {"type": "non_fast_forward"}],
    }
    if release_tags != expected_release_tags:
        fail("release-tag ruleset does not match the immutable-tag contract")


def validate_repository_control_contracts() -> None:
    main = json.loads((RULESETS / "main.json").read_text(encoding="utf-8"))
    release_tags = json.loads(
        (RULESETS / "release-tags.json").read_text(encoding="utf-8")
    )
    validate_ruleset_contracts(main, release_tags)

    ci_text = (WORKFLOWS / "ci.yml").read_text(encoding="utf-8")
    for context in REQUIRED_CHECKS:
        if f"name: {context}" not in ci_text:
            fail(f"CI is missing required-check job name: {context}")

    codeowners = (ROOT / ".github" / "CODEOWNERS").read_text(encoding="utf-8")
    for owned_path in ("/.github/", "/deploy/release/", "/security/"):
        if not any(line.startswith(f"{owned_path} ") for line in codeowners.splitlines()):
            fail(f"CODEOWNERS is missing release-sensitive path {owned_path}")


def validate_database_compatibility() -> None:
    compatibility_path = ROOT / "deploy" / "release" / "compatibility.json"
    compatibility = validate_compatibility(
        json.loads(compatibility_path.read_text(encoding="utf-8"))
    )
    migrations = []
    for path in (ROOT / "backend" / "launchforge-infrastructure" / "src" / "main" / "resources" / "db" / "migration").glob("V*__*.sql"):
        match = MIGRATION_PATTERN.search(path.name)
        if match:
            migrations.append(int(match.group(1)))
    if not migrations or max(migrations) != compatibility["database"]["migrationVersion"]:
        fail("deploy/release/compatibility.json must match the highest committed Flyway migration")


def validate_helm_release_contract() -> None:
    schema_ledger = (
        ROOT / "deploy" / "helm" / "launchforge" / "templates" / "database-schema-metadata.yaml"
    ).read_text(encoding="utf-8")
    required = (
        '"helm.sh/hook": pre-install,pre-upgrade',
        '"helm.sh/hook-weight": "-20"',
        '"helm.sh/hook-delete-policy": before-hook-creation',
        '"helm.sh/resource-policy": keep',
        "database-schema-version:",
    )
    for token in required:
        if token not in schema_ledger:
            fail(f"Database schema ledger is missing required fail-closed token: {token}")


def validate_security_exceptions() -> None:
    path = ROOT / "security" / "supply-chain-exceptions.json"
    value = json.loads(path.read_text(encoding="utf-8"))
    if set(value) != {"schemaVersion", "exceptions"} or value["schemaVersion"] != 1:
        fail("security/supply-chain-exceptions.json has an invalid envelope")
    if not isinstance(value["exceptions"], list):
        fail("security exceptions must be an array")
    required = {"id", "scanner", "scope", "rationale", "owner", "approvedBy", "expiresOn"}
    identifiers: set[str] = set()
    for index, exception in enumerate(value["exceptions"]):
        if not isinstance(exception, dict) or set(exception) != required:
            fail(f"Security exception {index} must contain exactly {sorted(required)}")
        if any(not isinstance(exception[key], str) or not exception[key].strip() for key in required):
            fail(f"Security exception {index} contains a blank field")
        try:
            expires_on = date.fromisoformat(exception["expiresOn"])
        except ValueError:
            fail(f"Security exception {index} has an invalid expiresOn date")
        if expires_on < date.today():
            fail(f"Security exception {index} expired on {expires_on.isoformat()}")
        identifier = exception["id"]
        if identifier in identifiers:
            fail(f"Security exception ID is duplicated: {identifier}")
        identifiers.add(identifier)
    ignore_files = list(ROOT.glob(".trivyignore*"))
    if ignore_files and not value["exceptions"]:
        fail("Trivy ignores require an explicit entry in security/supply-chain-exceptions.json")


def main() -> int:
    try:
        validate_action_pins()
        validate_workflow_contracts()
        validate_repository_control_contracts()
        validate_database_compatibility()
        validate_helm_release_contract()
        validate_security_exceptions()
    except (ManifestError, OSError, json.JSONDecodeError) as exception:
        print(f"supply-chain-validation: {exception}", file=sys.stderr)
        return 1
    print("Supply-chain repository contracts are valid.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
