# Codex Prompt 13 - CI/CD and Supply Chain

Implement **LF-1201 through LF-1205 only**.

Build:

- complete PR gates;
- dependency/secret/container scans;
- pinned GitHub Actions;
- release image SBOM/provenance;
- immutable image digest publication;
- staging deployment/smoke;
- protected production promotion using the same digest;
- compatibility-aware application rollback.

Never automatically reverse database migrations.

Do not put credentials in workflow files; prefer OIDC/workload identity where supported.

Run workflow/static validation where possible, update docs/status/changelog, and stop.
