# 24 - CI/CD and Release Supply Chain

## 1. Scope and release invariants

This document is the operating contract for LF-1201 through LF-1205. It covers pull-request gates,
dependency and secret controls, immutable release evidence, protected staging/production promotion,
and application rollback.

The non-negotiable invariants are:

- a release starts from an annotated `vMAJOR.MINOR.PATCH` tag whose commit is on `main` and has a
  successful `CI` run;
- the five deployable images are built once and identified by registry `sha256` digest;
- staging and production consume the same release manifest and image digests;
- a management edit reaches runtime only through the existing immutable configuration-publication
  workflow; a deployment never edits a published configuration revision;
- database migrations move forward only; application rollback never reverses Flyway migrations;
- configuration rollback remains a product operation that creates a newer immutable environment
  revision;
- no repository, workflow, image, release bundle, or committed Helm value contains credentials.

## 2. Pull-request and main-branch gates

`.github/workflows/ci.yml` supplies these required check names:

| Required check | Evidence |
|---|---|
| Dependency, secret, and repository security | Dependency review on PRs, proposed-history Gitleaks, Trivy dependency/misconfiguration scan |
| Java and JavaScript evaluator compatibility | Both evaluators execute the frozen language-neutral corpus |
| Java quality and integration | Full Maven reactor plus PostgreSQL integration profile |
| Frontend quality | Locked install, format, lint, type-check, unit, build, admin Playwright, and React demo Playwright |
| OIDC tenancy browser smoke | Real Keycloak login, tenant access, and logout against PostgreSQL-backed Control API |
| Repository contracts | Compose, Helm, documentation, workflow pinning, release tooling, and JSON contracts |

Configure the `main` ruleset in GitHub to require all six checks, one approving review, CODEOWNERS
review for owned paths, dismissal of stale approvals, conversation resolution, and a linear merge
history. Apply the rule to administrators, block force pushes and deletion, and permit no direct
push bypass. Protect `v*` tags from update or deletion. `.github/CODEOWNERS` assigns release,
workflow, and supply-chain policy changes to the repository owner.

The exact desired API request bodies are versioned in `.github/rulesets/main.json` and
`.github/rulesets/release-tags.json`. `eng/validate_supply_chain.py` rejects drift between the
`main` payload, CI job names, CODEOWNERS coverage, and the immutable-tag contract. Do not activate
the `main` payload until at least two trusted collaborators can participate: GitHub does not allow
an author to approve their own pull request, and this no-bypass policy would otherwise lock a
single-collaborator repository. After that prerequisite is satisfied, an administrator can create
or update the ruleset with the versioned payload and then verify the effective branch rules through
the GitHub API.

Repository workflows deliberately lack administration permission and cannot install these hosted
controls themselves. The owner must configure and periodically audit the live rulesets and
environments; a committed payload or green workflow without active enforcement is not equivalent
to protected `main`.

## 3. Dependencies, scanners, and exceptions

Dependabot checks GitHub Actions, Maven, pnpm/npm, and Docker inputs each week. Patch and minor
updates may be grouped but still require the entire PR gate. Major upgrades are deliberately
ignored by the bot and require a compatibility review, an explicit PR, and an ADR when they change
an architectural or contract decision. Lockfiles, the Maven wrapper, base-image digests, and full
Action SHAs remain authoritative.

Security gates are:

- dependency review rejects newly introduced HIGH or CRITICAL advisories;
- Gitleaks scans the pushed/PR commit range and rejects any detected secret;
- Trivy filesystem scanning rejects fixable HIGH or CRITICAL dependency or configuration findings;
- every release image is scanned by immutable digest and rejects fixable HIGH or CRITICAL
  vulnerabilities before staging;
- release Actions and repository workflow lint images are pinned by full immutable digest/SHA and
  checked by `eng/validate_supply_chain.py`.

An exception is allowed only when remediation is impossible within the release window and the
residual risk is explicitly accepted. Add exactly one record to
`security/supply-chain-exceptions.json` with a unique ID, scanner, narrow scope, rationale, owner,
approver, and ISO expiry date. An expired or malformed record fails CI. Scanner ignore files are
forbidden unless at least one governed record exists. There are no active exceptions at this
baseline. Never suppress a secret finding; rotate/revoke the credential and remove it from history
under an incident procedure.

## 4. Immutable release workflow

`.github/workflows/release.yml` runs only for an annotated semantic-version tag. It rejects a tag
outside `main`, a commit without successful CI, or a tag that already has a GitHub Release. It then:

1. builds management, Config Edge, Event Worker, web, and migrator images exactly once;
2. publishes each image to GHCR with readable tag/SHA aliases but records only its immutable
   `repository@sha256:digest` identity;
3. scans the immutable digest, generates an SPDX JSON SBOM, and attaches GitHub build-provenance and
   SBOM attestations to that digest;
4. creates `release/release-manifest.json` with tag, full Git SHA, source repository, compatibility
   versions, and all five digests;
5. deploys that manifest to the protected `staging` environment with forward migrations enabled;
6. executes the staging smoke; and
7. creates the immutable GitHub Release with the manifest and all SBOMs only after staging succeeds.

`deploy/release/compatibility.json` is the reviewed compatibility declaration. Its database
migration version must match the highest committed Flyway migration. Snapshot and evaluation
algorithm versions are also recorded in the release manifest and the deployed
`release-metadata` ConfigMap. A separate pre-migration `database-schema` ConfigMap records the
target schema before Flyway runs. This deliberately fails closed if migration succeeds but the
later workload rollout fails: a subsequent rollback cannot trust a stale lower schema. It is kept
with the external database and replaced before each future Helm operation. `eng/release_manifest.py`
rejects missing images, mutable references, tag/SHA mismatches, and invalid compatibility metadata.

Verify a downloaded release before use:

```powershell
gh release download v1.2.3 --pattern release-manifest.json --pattern "*.spdx.json" --dir release-proof
python eng/release_manifest.py validate --manifest release-proof/release-manifest.json --expected-tag v1.2.3 --expected-sha <40-character-sha> --expected-repository Yashraj-Rathore/LaunchForge
docker buildx imagetools inspect ghcr.io/yashraj-rathore/launchforge-management@sha256:<digest>
gh attestation verify oci://ghcr.io/yashraj-rathore/launchforge-management@sha256:<digest> --repo Yashraj-Rathore/LaunchForge
```

Repeat the image and attestation checks for all five manifest entries. Inspect the SPDX files with
the approved vulnerability/license tooling when release policy requires a human review.

## 5. Staging environment and smoke

Create a GitHub Environment named `staging`. The cloud-neutral workflow requests an OIDC token so a
provider-specific workload-identity login can replace static cluster credentials. Until that
provider step is selected, `LAUNCHFORGE_KUBE_CONFIG_B64` must be a narrowly scoped, short-lived
kubeconfig stored as an environment secret. Never commit it. `LAUNCHFORGE_HELM_VALUES_B64` is a
protected transport for non-secret environment values; those values must reference a Kubernetes
Secret managed outside Git rather than contain secret material.

Configure staging with:

| Kind | Name | Purpose |
|---|---|---|
| secret | `LAUNCHFORGE_KUBE_CONFIG_B64` | Base64 kubeconfig until provider OIDC is wired |
| secret | `LAUNCHFORGE_HELM_VALUES_B64` | Base64 non-secret Helm environment override |
| secret | `LAUNCHFORGE_SMOKE_PASSWORD` | Fictional least-privilege staging operator password |
| variable | `LAUNCHFORGE_HELM_RELEASE` | Helm release name; defaults to `launchforge` |
| variable | `LAUNCHFORGE_NAMESPACE` | Kubernetes namespace; defaults to `launchforge` |
| variable | `LAUNCHFORGE_WEB_URL` | HTTPS same-origin web entry point |
| variable | `LAUNCHFORGE_EDGE_URL` | HTTPS Config Edge entry point |
| variable | `LAUNCHFORGE_SMOKE_USERNAME` | Fictional staging OIDC operator |
| variable | `LAUNCHFORGE_SMOKE_PROJECT_ID` | Dedicated fictional smoke project UUID |
| variable | `LAUNCHFORGE_SMOKE_ENVIRONMENT_ID` | Dedicated fictional smoke environment UUID |

The dedicated project must contain no customer data. The smoke proves HTTPS, real OIDC login,
management mutation, publish, Config Edge snapshot delivery, revision-only SSE, Java SDK streaming
refresh, and the Java demo kill-switch change without application redeploy. It creates a one-time
server SDK key and revokes it during cleanup. Playwright tracing is disabled for this external flow
so the one-time secret is not persisted in an artifact.

## 6. Production promotion

Create a GitHub Environment named `production` with at least one required reviewer, prevent the
requester from approving their own deployment, restrict deployment to protected release sources,
and store production-scoped cluster access and non-secret Helm overrides under the same names used
by staging. Prefer provider workload identity through the workflow's `id-token: write` permission;
do not add cloud access keys to repository or workflow source.

Run `Promote production` with the successful `Release` workflow run ID and its exact tag. The
workflow verifies the run originated in this repository from that pushed tag, completed
successfully (including staging), and produced a manifest matching the checked-out tag and SHA.
After protected approval it checks database compatibility, enables the forward migration Job, and
deploys the same digest set. A fresh image build is neither required nor allowed. The deployed
ConfigMap SHA must equal the manifest SHA before promotion is considered successful.

## 7. Application and configuration rollback

Run `Roll back production application` with an incident/change reference and a previously
staging-tested release run/tag. Protected production approval still applies. The workflow reads the
current recorded database schema, blocks a candidate whose application compatibility range excludes
that schema, renders the candidate digests with migrations disabled, verifies that no migration Job
is present, deploys, and proves the database schema record did not move backward.

If no older application supports the current schema, do not force the rollback. Forward-fix the
application or restore the database through the separately approved disaster-recovery procedure.
Flyway migrations are never automatically undone.

To restore customer flag behavior, do not run the application workflow. Use LaunchForge revision
history to publish a newer configuration revision whose content restores the selected historical
state. This preserves ordering, audit, SDK convergence, and immutable history.

## 8. Local repository verification

Run before proposing a release-policy change:

```powershell
python -m unittest discover -s eng/tests -p "test_*.py"
python eng/validate_supply_chain.py
pnpm format:check
pnpm lint
pnpm typecheck
pnpm test
pnpm build
helm lint deploy/helm/launchforge --strict
helm template launchforge deploy/helm/launchforge --namespace launchforge
```

CI additionally runs Actionlint, full Maven/integration/browser gates, dependency/secret scanning,
and the real staging workflow. A local render proves repository mechanics; it does not claim that
GitHub Environment approval, GHCR publication, cloud identity, or a live staging/production cluster
has been exercised.
