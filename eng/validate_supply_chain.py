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
ACTION_PATTERN = re.compile(r"^\s*-?\s*uses:\s*([^@\s]+)@([^\s#]+)(?:\s+#\s*(\S+))?", re.MULTILINE)
SHA_PATTERN = re.compile(r"[0-9a-f]{40}\Z")
MIGRATION_PATTERN = re.compile(r"V([1-9][0-9]*)__[^/\\]+\.sql\Z")
REQUIRED_WORKFLOWS = {
    "ci.yml",
    "release.yml",
    "promote-production.yml",
    "rollback-production.yml",
}


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
