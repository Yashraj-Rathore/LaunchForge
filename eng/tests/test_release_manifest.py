from __future__ import annotations

import json
import tempfile
import unittest
from argparse import Namespace
from pathlib import Path

from eng.release_manifest import (
    IMAGE_NAMES,
    ManifestError,
    assert_compatible,
    command_render_values,
    validate_manifest,
)


class ReleaseManifestTest(unittest.TestCase):
    def manifest(self) -> dict[str, object]:
        return {
            "schemaVersion": 1,
            "releaseTag": "v1.2.3",
            "gitSha": "a" * 40,
            "sourceRepository": "example/launchforge",
            "createdAt": "2026-08-20T12:00:00Z",
            "compatibility": {
                "schemaVersion": 1,
                "database": {
                    "migrationVersion": 6,
                    "applicationMinimum": 6,
                    "applicationMaximum": 7,
                },
                "snapshotSchemaVersions": [1],
                "evaluationAlgorithmVersions": [1],
            },
            "images": {
                name: {
                    "repository": f"ghcr.io/example/launchforge-{name}",
                    "digest": "sha256:" + str(index) * 64,
                }
                for index, name in enumerate(IMAGE_NAMES, start=1)
            },
        }

    def test_valid_manifest_and_compatible_promotion(self) -> None:
        manifest = validate_manifest(self.manifest())
        assert_compatible(manifest, 5, "promotion")

    def test_rejects_mutable_or_malformed_image_reference(self) -> None:
        manifest = self.manifest()
        manifest["images"]["web"]["digest"] = "latest"
        with self.assertRaisesRegex(ManifestError, "immutable sha256"):
            validate_manifest(manifest)

    def test_blocks_incompatible_application_rollback(self) -> None:
        manifest = validate_manifest(self.manifest())
        with self.assertRaisesRegex(ManifestError, "Rollback blocked"):
            assert_compatible(manifest, 8, "rollback")

    def test_rollback_values_disable_migrations_and_keep_current_schema(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest_path = root / "release.json"
            output_path = root / "values.yaml"
            manifest_path.write_text(json.dumps(self.manifest()), encoding="utf-8")
            command_render_values(
                Namespace(
                    manifest=manifest_path,
                    output=output_path,
                    database_schema_version=7,
                    migrations_enabled="false",
                )
            )
            rendered = output_path.read_text(encoding="utf-8")
            self.assertIn("databaseSchemaVersion: 7", rendered)
            self.assertIn("migrations:\n  enabled: false", rendered)
            self.assertNotIn(":latest", rendered)


if __name__ == "__main__":
    unittest.main()
