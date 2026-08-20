#!/usr/bin/env python3
"""Create and validate immutable LaunchForge release manifests."""

from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime
from pathlib import Path
from typing import Any


IMAGE_NAMES = ("management", "config-edge", "event-worker", "web", "migrator")
HELM_IMAGE_KEYS = {
    "management": "management",
    "config-edge": "configEdge",
    "event-worker": "eventWorker",
    "web": "web",
    "migrator": "migrator",
}
DIGEST_PATTERN = re.compile(r"sha256:[0-9a-f]{64}\Z")
GIT_SHA_PATTERN = re.compile(r"[0-9a-f]{40}\Z")
RELEASE_TAG_PATTERN = re.compile(
    r"v(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)"
    r"(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?\Z"
)
REPOSITORY_PATTERN = re.compile(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\Z")
IMAGE_REPOSITORY_PATTERN = re.compile(r"ghcr\.io/[a-z0-9_.-]+/[a-z0-9_.-]+\Z")


class ManifestError(ValueError):
    """Raised when release metadata violates the versioned contract."""


def load_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise ManifestError(f"Cannot read valid JSON from {path}: {exception}") from exception


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ManifestError(f"{label} must be a JSON object")
    return value


def require_exact_keys(value: dict[str, Any], expected: set[str], label: str) -> None:
    actual = set(value)
    if actual != expected:
        missing = sorted(expected - actual)
        extra = sorted(actual - expected)
        raise ManifestError(f"{label} keys are invalid; missing={missing}, extra={extra}")


def require_positive_int(value: Any, label: str, *, allow_zero: bool = False) -> int:
    minimum = 0 if allow_zero else 1
    if isinstance(value, bool) or not isinstance(value, int) or value < minimum:
        raise ManifestError(f"{label} must be an integer greater than or equal to {minimum}")
    return value


def require_version_list(value: Any, label: str) -> list[int]:
    if not isinstance(value, list) or not value:
        raise ManifestError(f"{label} must be a non-empty array")
    versions = [require_positive_int(item, label) for item in value]
    if versions != sorted(set(versions)):
        raise ManifestError(f"{label} must contain sorted unique versions")
    return versions


def validate_compatibility(value: Any) -> dict[str, Any]:
    compatibility = require_object(value, "compatibility")
    require_exact_keys(
        compatibility,
        {
            "schemaVersion",
            "database",
            "snapshotSchemaVersions",
            "evaluationAlgorithmVersions",
        },
        "compatibility",
    )
    if compatibility["schemaVersion"] != 1:
        raise ManifestError("compatibility.schemaVersion must be 1")
    database = require_object(compatibility["database"], "compatibility.database")
    require_exact_keys(
        database,
        {"migrationVersion", "applicationMinimum", "applicationMaximum"},
        "compatibility.database",
    )
    migration = require_positive_int(database["migrationVersion"], "database.migrationVersion")
    minimum = require_positive_int(database["applicationMinimum"], "database.applicationMinimum")
    maximum = require_positive_int(database["applicationMaximum"], "database.applicationMaximum")
    if minimum > maximum or not minimum <= migration <= maximum:
        raise ManifestError(
            "The release migration version must be within the application's supported database range"
        )
    require_version_list(compatibility["snapshotSchemaVersions"], "snapshotSchemaVersions")
    require_version_list(
        compatibility["evaluationAlgorithmVersions"], "evaluationAlgorithmVersions"
    )
    return compatibility


def validate_image(value: Any, label: str) -> dict[str, str]:
    image = require_object(value, label)
    require_exact_keys(image, {"repository", "digest"}, label)
    repository = image["repository"]
    digest = image["digest"]
    if not isinstance(repository, str) or not IMAGE_REPOSITORY_PATTERN.fullmatch(repository):
        raise ManifestError(f"{label}.repository must be a lowercase ghcr.io repository")
    if not isinstance(digest, str) or not DIGEST_PATTERN.fullmatch(digest):
        raise ManifestError(f"{label}.digest must be an immutable sha256 digest")
    return {"repository": repository, "digest": digest}


def validate_timestamp(value: Any) -> str:
    if not isinstance(value, str) or not value.endswith("Z"):
        raise ManifestError("createdAt must be a UTC RFC 3339 timestamp ending in Z")
    try:
        datetime.fromisoformat(value.removesuffix("Z") + "+00:00")
    except ValueError as exception:
        raise ManifestError("createdAt must be a valid RFC 3339 timestamp") from exception
    return value


def validate_manifest(value: Any) -> dict[str, Any]:
    manifest = require_object(value, "release manifest")
    require_exact_keys(
        manifest,
        {
            "schemaVersion",
            "releaseTag",
            "gitSha",
            "sourceRepository",
            "createdAt",
            "compatibility",
            "images",
        },
        "release manifest",
    )
    if manifest["schemaVersion"] != 1:
        raise ManifestError("release manifest schemaVersion must be 1")
    if not isinstance(manifest["releaseTag"], str) or not RELEASE_TAG_PATTERN.fullmatch(
        manifest["releaseTag"]
    ):
        raise ManifestError("releaseTag must be a v-prefixed semantic version")
    if not isinstance(manifest["gitSha"], str) or not GIT_SHA_PATTERN.fullmatch(
        manifest["gitSha"]
    ):
        raise ManifestError("gitSha must be a lowercase 40-character commit SHA")
    if not isinstance(manifest["sourceRepository"], str) or not REPOSITORY_PATTERN.fullmatch(
        manifest["sourceRepository"]
    ):
        raise ManifestError("sourceRepository must use owner/repository form")
    validate_timestamp(manifest["createdAt"])
    validate_compatibility(manifest["compatibility"])
    images = require_object(manifest["images"], "images")
    require_exact_keys(images, set(IMAGE_NAMES), "images")
    for name in IMAGE_NAMES:
        validate_image(images[name], f"images.{name}")
    return manifest


def command_record_image(arguments: argparse.Namespace) -> None:
    if arguments.name not in IMAGE_NAMES:
        raise ManifestError(f"Unknown image name: {arguments.name}")
    image = validate_image(
        {"repository": arguments.repository, "digest": arguments.digest}, "image"
    )
    write_json(arguments.output, {"name": arguments.name, **image})


def command_assemble(arguments: argparse.Namespace) -> None:
    compatibility = validate_compatibility(load_json(arguments.compatibility))
    images: dict[str, dict[str, str]] = {}
    for path in sorted(arguments.metadata_dir.glob("*.json")):
        record = require_object(load_json(path), f"image record {path}")
        require_exact_keys(record, {"name", "repository", "digest"}, f"image record {path}")
        name = record["name"]
        if name not in IMAGE_NAMES:
            raise ManifestError(f"Image record {path} has an unknown name")
        if name in images:
            raise ManifestError(f"Image metadata contains duplicate record for {name}")
        images[name] = validate_image(
            {"repository": record["repository"], "digest": record["digest"]},
            f"image record {name}",
        )
    if set(images) != set(IMAGE_NAMES):
        raise ManifestError(
            f"Image metadata must contain exactly {list(IMAGE_NAMES)}; found={sorted(images)}"
        )
    manifest = {
        "schemaVersion": 1,
        "releaseTag": arguments.release_tag,
        "gitSha": arguments.git_sha,
        "sourceRepository": arguments.source_repository,
        "createdAt": arguments.created_at,
        "compatibility": compatibility,
        "images": {name: images[name] for name in IMAGE_NAMES},
    }
    validate_manifest(manifest)
    write_json(arguments.output, manifest)


def command_validate(arguments: argparse.Namespace) -> None:
    manifest = validate_manifest(load_json(arguments.manifest))
    if arguments.expected_tag and manifest["releaseTag"] != arguments.expected_tag:
        raise ManifestError("Release manifest tag does not match the requested release")
    if arguments.expected_sha and manifest["gitSha"] != arguments.expected_sha:
        raise ManifestError("Release manifest SHA does not match the requested commit")
    if arguments.expected_repository and manifest["sourceRepository"] != arguments.expected_repository:
        raise ManifestError("Release manifest repository does not match the current repository")


def assert_compatible(manifest: dict[str, Any], current: int, mode: str) -> None:
    database = manifest["compatibility"]["database"]
    migration = database["migrationVersion"]
    minimum = database["applicationMinimum"]
    maximum = database["applicationMaximum"]
    if mode == "promotion":
        if current > migration:
            raise ManifestError(
                f"Release migration {migration} cannot run against newer recorded schema {current}"
            )
        if not minimum <= migration <= maximum:
            raise ManifestError("Application is incompatible with its post-migration schema")
        return
    if not minimum <= current <= maximum:
        raise ManifestError(
            f"Rollback blocked: application supports database schemas {minimum}..{maximum}, "
            f"but production records schema {current}"
        )


def command_assert_compatible(arguments: argparse.Namespace) -> None:
    manifest = validate_manifest(load_json(arguments.manifest))
    assert_compatible(manifest, arguments.current_database_schema, arguments.mode)


def yaml_scalar(value: str) -> str:
    return json.dumps(value, ensure_ascii=True)


def command_render_values(arguments: argparse.Namespace) -> None:
    manifest = validate_manifest(load_json(arguments.manifest))
    database = manifest["compatibility"]["database"]
    database_version = (
        arguments.database_schema_version
        if arguments.database_schema_version is not None
        else database["migrationVersion"]
    )
    require_positive_int(database_version, "database schema version", allow_zero=True)
    lines = ["images:"]
    for name in IMAGE_NAMES:
        image = manifest["images"][name]
        lines.extend(
            [
                f"  {HELM_IMAGE_KEYS[name]}:",
                f"    repository: {yaml_scalar(image['repository'])}",
                f"    tag: {yaml_scalar(manifest['releaseTag'])}",
                f"    digest: {yaml_scalar(image['digest'])}",
            ]
        )
    lines.extend(
        [
            "release:",
            f"  version: {yaml_scalar(manifest['releaseTag'])}",
            f"  revision: {yaml_scalar(manifest['gitSha'])}",
            f"  databaseSchemaVersion: {database_version}",
            "  snapshotSchemaVersions:",
            *[
                f"    - {version}"
                for version in manifest["compatibility"]["snapshotSchemaVersions"]
            ],
            "  evaluationAlgorithmVersions:",
            *[
                f"    - {version}"
                for version in manifest["compatibility"]["evaluationAlgorithmVersions"]
            ],
            "migrations:",
            f"  enabled: {'true' if arguments.migrations_enabled == 'true' else 'false'}",
        ]
    )
    arguments.output.parent.mkdir(parents=True, exist_ok=True)
    arguments.output.write_text("\n".join(lines) + "\n", encoding="utf-8")


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description=__doc__)
    commands = root.add_subparsers(dest="command", required=True)

    record = commands.add_parser("record-image")
    record.add_argument("--name", required=True)
    record.add_argument("--repository", required=True)
    record.add_argument("--digest", required=True)
    record.add_argument("--output", type=Path, required=True)
    record.set_defaults(handler=command_record_image)

    assemble = commands.add_parser("assemble")
    assemble.add_argument("--metadata-dir", type=Path, required=True)
    assemble.add_argument("--compatibility", type=Path, required=True)
    assemble.add_argument("--release-tag", required=True)
    assemble.add_argument("--git-sha", required=True)
    assemble.add_argument("--source-repository", required=True)
    assemble.add_argument("--created-at", required=True)
    assemble.add_argument("--output", type=Path, required=True)
    assemble.set_defaults(handler=command_assemble)

    validate = commands.add_parser("validate")
    validate.add_argument("--manifest", type=Path, required=True)
    validate.add_argument("--expected-tag")
    validate.add_argument("--expected-sha")
    validate.add_argument("--expected-repository")
    validate.set_defaults(handler=command_validate)

    compatible = commands.add_parser("assert-compatible")
    compatible.add_argument("--manifest", type=Path, required=True)
    compatible.add_argument("--current-database-schema", type=int, required=True)
    compatible.add_argument("--mode", choices=("promotion", "rollback"), required=True)
    compatible.set_defaults(handler=command_assert_compatible)

    render = commands.add_parser("render-values")
    render.add_argument("--manifest", type=Path, required=True)
    render.add_argument("--output", type=Path, required=True)
    render.add_argument("--database-schema-version", type=int)
    render.add_argument("--migrations-enabled", choices=("true", "false"), required=True)
    render.set_defaults(handler=command_render_values)
    return root


def main() -> int:
    try:
        arguments = parser().parse_args()
        arguments.handler(arguments)
    except ManifestError as exception:
        print(f"release-manifest: {exception}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
