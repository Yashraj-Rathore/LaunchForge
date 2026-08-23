#!/usr/bin/env python3
"""Lightweight documentation/package consistency checks."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
errors = []

required = [
    "README.md",
    "AGENTS.md",
    "CODEX_START_HERE.md",
    "CODEX_PROMPT_SEQUENCE.md",
    "PROJECT_STATUS.md",
    "PACKAGE_MANIFEST.md",
    "docs/01_PRODUCT_REQUIREMENTS.md",
    "docs/02_SYSTEM_ARCHITECTURE.md",
    "docs/05_FLAG_EVALUATION_ENGINE.md",
    "docs/15_BACKLOG_AND_ACCEPTANCE.md",
    "docs/19_TECHNOLOGY_BASELINE.md",
    "templates/definition-of-done.md",
]
for rel in required:
    if not (ROOT / rel).exists():
        errors.append(f"missing required file: {rel}")

backlog = (ROOT / "docs/15_BACKLOG_AND_ACCEPTANCE.md").read_text(encoding="utf-8")
ids = re.findall(r"\bLF-\d{4}\b", backlog)
unique = sorted(set(ids))
if len(unique) < 60:
    errors.append(f"expected >=60 unique backlog IDs, found {len(unique)}")

prompts = sorted((ROOT / "codex-prompts").glob("*.md"))
if len(prompts) != 16:
    errors.append(f"expected 16 numbered Codex prompt files, found {len(prompts)}")

numbered_docs = sorted(
    path for path in (ROOT / "docs").glob("*.md") if re.match(r"\d{2}_", path.name)
)
adrs = sorted((ROOT / "docs" / "decisions").glob("ADR-*.md"))
maven_modules = re.findall(
    r"<module>[^<]+</module>", (ROOT / "pom.xml").read_text(encoding="utf-8")
)
manifest = (ROOT / "PACKAGE_MANIFEST.md").read_text(encoding="utf-8")
manifest_contract = (
    (f"{len(numbered_docs)} numbered documents", "numbered documentation inventory"),
    (f"{len(adrs)} ADRs", "ADR inventory"),
    (f"{len(prompts)} ordered Codex prompts", "prompt inventory"),
    (f"{len(maven_modules)} Maven child modules", "Maven module inventory"),
    ("M1-M13 complete", "implemented milestone state"),
    ("docs/27_FINAL_ARCHITECTURE_REVIEW.md", "current final-review document"),
    ("backend/launchforge-control-api/", "Control API implementation"),
    ("frontend/admin-web/", "admin console implementation"),
    ("sdks/java/launchforge-java-sdk/", "Java SDK implementation"),
    ("sdks/javascript/packages/core/", "JavaScript SDK implementation"),
    ("demos/react-storefront/", "storefront demo implementation"),
    ("tests/integration-tests/", "integration-test implementation"),
    ("deploy/helm/launchforge/", "Helm deployment assets"),
)
for token, description in manifest_contract:
    if token not in manifest:
        errors.append(f"PACKAGE_MANIFEST is missing current {description}: {token}")
if (
    "implemented M0 foundation" in manifest
    or "product behavior remains intentionally deferred" in manifest.lower()
):
    errors.append("PACKAGE_MANIFEST still contains the obsolete M0-only implementation claim")

# Validate every milestone issue listed in PROJECT_STATUS exists in backlog.
status = (ROOT / "PROJECT_STATUS.md").read_text(encoding="utf-8")
for issue in sorted(set(re.findall(r"\bLF-\d{4}\b", status))):
    if issue not in unique:
        errors.append(f"PROJECT_STATUS references missing backlog ID {issue}")

# Generated master must explicitly warn not to edit.
master = ROOT / "CODEX_MASTER_IMPLEMENTATION_SPEC.md"
if master.exists():
    text = master.read_text(encoding="utf-8")
    if "GENERATED FILE" not in text:
        errors.append("master specification lacks generated-file warning")

if errors:
    print("Documentation validation FAILED:")
    for err in errors:
        print(f"- {err}")
    sys.exit(1)

print(f"Documentation validation passed: {len(unique)} backlog issues, {len(prompts)} Codex prompts.")

if __name__ == "__main__":
    pass
