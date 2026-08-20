#!/usr/bin/env python3
"""Generate the deterministic fictional Northstar Commerce demo seed."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "deploy" / "local" / "tenancy-seed.sql"
GENERATED_AT = "2026-08-20T12:00:00Z"
ORGANIZATION_ID = "10000000-0000-0000-0000-000000000001"
PROJECT_ID = "40000000-0000-0000-0000-000000000001"
DEVELOPMENT_ID = "50000000-0000-0000-0000-000000000001"
STAGING_ID = "50000000-0000-0000-0000-000000000002"
PRODUCTION_ID = "50000000-0000-0000-0000-000000000003"
BROWSER_KEY_SUFFIX = "NorthstarDemoClientKey0000000001"


def canonical_json(value: Any) -> str:
    return json.dumps(
        value,
        ensure_ascii=False,
        allow_nan=False,
        separators=(",", ":"),
        sort_keys=True,
    )


def sql_string(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def sql_json(value: Any) -> str:
    return f"{sql_string(canonical_json(value))}::jsonb"


def variation(
    identifier: str, key: str, name: str, canonical_value: str, runtime_value: Any
) -> dict[str, Any]:
    return {
        "id": identifier,
        "key": key,
        "name": name,
        "canonicalValue": canonical_value,
        "runtimeValue": runtime_value,
    }


FLAGS: list[dict[str, Any]] = [
    {
        "id": "60000000-0000-0000-0000-000000000001",
        "key": "new-checkout",
        "name": "New checkout",
        "type": "BOOLEAN",
        "clientVisible": True,
        "variations": [
            variation(
                "61000000-0000-0000-0000-000000000001", "off", "Classic checkout", "false", False
            ),
            variation(
                "61000000-0000-0000-0000-000000000002", "on", "Express checkout", "true", True
            ),
        ],
        "enabled": True,
        "off": "61000000-0000-0000-0000-000000000001",
        "default": "61000000-0000-0000-0000-000000000001",
        "salt": "stable_salt_1234",
        "rules": [
            {
                "id": "62000000-0000-0000-0000-000000000001",
                "name": "Canadian Pro customers",
                "conditions": [
                    {
                        "attribute": "country",
                        "attributeType": "STRING",
                        "operator": "EQUALS",
                        "values": ["CA"],
                    },
                    {
                        "attribute": "plan",
                        "attributeType": "STRING",
                        "operator": "EQUALS",
                        "values": ["pro"],
                    },
                ],
                "variationId": "61000000-0000-0000-0000-000000000002",
            }
        ],
        "rollout": {
            "subjectAttribute": "userId",
            "allocations": [
                {
                    "variationId": "61000000-0000-0000-0000-000000000002",
                    "weight": 10_000,
                },
                {
                    "variationId": "61000000-0000-0000-0000-000000000001",
                    "weight": 90_000,
                },
            ],
        },
        "summary": "Target Canadian Pro customers and begin a stable ten-percent rollout.",
    },
    {
        "id": "60000000-0000-0000-0000-000000000002",
        "key": "search-ranking",
        "name": "Search ranking model",
        "type": "STRING",
        "clientVisible": True,
        "variations": [
            variation(
                "61000000-0000-0000-0000-000000000003",
                "baseline",
                "Baseline ranking",
                "lexical-v1",
                "lexical-v1",
            ),
            variation(
                "61000000-0000-0000-0000-000000000004",
                "hybrid",
                "Hybrid ranking",
                "hybrid-v2",
                "hybrid-v2",
            ),
        ],
        "enabled": True,
        "off": "61000000-0000-0000-0000-000000000003",
        "default": "61000000-0000-0000-0000-000000000003",
        "salt": "northstar_search_2026",
        "rules": [
            {
                "id": "62000000-0000-0000-0000-000000000002",
                "name": "Internal search testers",
                "conditions": [
                    {
                        "attribute": "plan",
                        "attributeType": "STRING",
                        "operator": "EQUALS",
                        "values": ["internal"],
                    }
                ],
                "variationId": "61000000-0000-0000-0000-000000000004",
            }
        ],
        "rollout": None,
        "summary": "Keep the baseline ranking except for the fictional internal cohort.",
    },
    {
        "id": "60000000-0000-0000-0000-000000000003",
        "key": "recommendation-card",
        "name": "Recommendation card",
        "type": "JSON",
        "clientVisible": True,
        "variations": [
            variation(
                "61000000-0000-0000-0000-000000000005",
                "compact",
                "Compact card",
                canonical_json({"layout": "compact", "maxItems": 3, "showBadges": False}),
                {"layout": "compact", "maxItems": 3, "showBadges": False},
            ),
            variation(
                "61000000-0000-0000-0000-000000000006",
                "immersive",
                "Immersive card",
                canonical_json({"layout": "immersive", "maxItems": 6, "showBadges": True}),
                {"layout": "immersive", "maxItems": 6, "showBadges": True},
            ),
        ],
        "enabled": True,
        "off": "61000000-0000-0000-0000-000000000005",
        "default": "61000000-0000-0000-0000-000000000005",
        "salt": "northstar_cards_2026",
        "rules": [
            {
                "id": "62000000-0000-0000-0000-000000000003",
                "name": "Pro recommendation layout",
                "conditions": [
                    {
                        "attribute": "plan",
                        "attributeType": "STRING",
                        "operator": "EQUALS",
                        "values": ["pro"],
                    }
                ],
                "variationId": "61000000-0000-0000-0000-000000000006",
            }
        ],
        "rollout": None,
        "summary": "Use an immersive fictional recommendation card for Pro visitors.",
    },
    {
        "id": "60000000-0000-0000-0000-000000000004",
        "key": "fraud-review-threshold",
        "name": "Fraud review threshold",
        "type": "NUMBER",
        "clientVisible": False,
        "variations": [
            variation(
                "61000000-0000-0000-0000-000000000007",
                "standard",
                "Standard threshold",
                "75",
                75,
            ),
            variation(
                "61000000-0000-0000-0000-000000000008",
                "strict",
                "Strict threshold",
                "90",
                90,
            ),
        ],
        "enabled": True,
        "off": "61000000-0000-0000-0000-000000000007",
        "default": "61000000-0000-0000-0000-000000000007",
        "salt": "northstar_fraud_2026",
        "rules": [
            {
                "id": "62000000-0000-0000-0000-000000000004",
                "name": "High-risk review",
                "conditions": [
                    {
                        "attribute": "riskScore",
                        "attributeType": "NUMBER",
                        "operator": "GTE",
                        "values": ["80"],
                    }
                ],
                "variationId": "61000000-0000-0000-0000-000000000008",
            }
        ],
        "rollout": None,
        "summary": "Keep the server-only review threshold explicit and inspectable by operators.",
    },
]


ENVIRONMENTS = [
    (DEVELOPMENT_ID, "development", "Development", "DEVELOPMENT", 1, 1),
    (STAGING_ID, "staging", "Staging", "STAGING", 0, 0),
    (PRODUCTION_ID, "production", "Production", "PRODUCTION", 0, 0),
]


def snapshot_rule(rule: dict[str, Any], variation_keys: dict[str, str]) -> dict[str, Any]:
    return {
        "id": rule["id"],
        "conditions": [
            {
                "attribute": condition["attribute"],
                "attributeType": condition["attributeType"].lower(),
                "operator": condition["operator"],
                "values": [
                    json.loads(value)
                    if condition["attributeType"] in {"NUMBER", "BOOLEAN"}
                    else value
                    for value in condition["values"]
                ],
            }
            for condition in rule["conditions"]
        ],
        "variation": variation_keys[rule["variationId"]],
    }


def runtime_flag(flag: dict[str, Any]) -> dict[str, Any]:
    keys = {candidate["id"]: candidate["key"] for candidate in flag["variations"]}
    result: dict[str, Any] = {
        "type": flag["type"].lower(),
        "enabled": flag["enabled"],
        "clientVisible": flag["clientVisible"],
        "variations": [
            {"id": candidate["key"], "value": candidate["runtimeValue"]}
            for candidate in flag["variations"]
        ],
        "offVariation": keys[flag["off"]],
        "defaultVariation": keys[flag["default"]],
        "rules": [snapshot_rule(rule, keys) for rule in flag["rules"]],
    }
    if flag["rollout"] is not None:
        result["rollout"] = {
            "attribute": flag["rollout"]["subjectAttribute"],
            "salt": flag["salt"],
            "weights": [
                {"variation": keys[allocation["variationId"]], "weight": allocation["weight"]}
                for allocation in flag["rollout"]["allocations"]
            ],
        }
    return result


def development_snapshot() -> tuple[dict[str, Any], str, str]:
    projection = {
        "schemaVersion": 1,
        "algorithmVersion": 1,
        "projectKey": "storefront",
        "environmentKey": "development",
        "revision": 1,
        "generatedAt": GENERATED_AT,
        "flags": {flag["key"]: runtime_flag(flag) for flag in FLAGS},
    }
    checksum = hashlib.sha256(canonical_json(projection).encode("utf-8")).hexdigest()
    snapshot = {**projection, "checksum": checksum}
    return snapshot, canonical_json(snapshot), checksum


def tuple_rows(rows: list[list[str]], indent: str = "    ") -> str:
    return (",\n" + indent).join("(" + ", ".join(row) + ")" for row in rows)


def render_sql() -> str:
    snapshot, canonical_snapshot, checksum = development_snapshot()
    timestamp = "TIMESTAMPTZ '2026-08-20 12:00:00Z'"
    environment_rows = [
        [
            sql_string(identifier),
            sql_string(ORGANIZATION_ID),
            sql_string(PROJECT_ID),
            sql_string(key),
            sql_string(name),
            sql_string(kind),
            sql_string("ACTIVE"),
            str(revision),
            str(version),
            timestamp,
            timestamp,
        ]
        for identifier, key, name, kind, revision, version in ENVIRONMENTS
    ]
    flag_rows = [
        [
            sql_string(flag["id"]),
            sql_string(ORGANIZATION_ID),
            sql_string(PROJECT_ID),
            sql_string(flag["key"]),
            sql_string(flag["name"]),
            sql_string(flag["type"]),
            "TRUE" if flag["clientVisible"] else "FALSE",
            sql_string("ACTIVE"),
            "0",
            timestamp,
            timestamp,
        ]
        for flag in FLAGS
    ]
    variation_rows: list[list[str]] = []
    for flag in FLAGS:
        for position, candidate in enumerate(flag["variations"]):
            variation_rows.append(
                [
                    sql_string(candidate["id"]),
                    sql_string(ORGANIZATION_ID),
                    sql_string(PROJECT_ID),
                    sql_string(flag["id"]),
                    sql_string(candidate["key"]),
                    sql_string(candidate["name"]),
                    sql_string(candidate["canonicalValue"]),
                    str(position),
                ]
            )
    config_rows: list[list[str]] = []
    for environment_id, *_ in ENVIRONMENTS:
        for flag in FLAGS:
            config_rows.append(
                [
                    sql_string(ORGANIZATION_ID),
                    sql_string(PROJECT_ID),
                    sql_string(environment_id),
                    sql_string(flag["id"]),
                    "TRUE" if flag["enabled"] else "FALSE",
                    sql_string(flag["default"]),
                    sql_string(flag["off"]),
                    sql_string(flag["salt"]),
                    sql_json(flag["rules"]),
                    "NULL" if flag["rollout"] is None else sql_json(flag["rollout"]),
                    sql_string(flag["summary"]),
                    "0",
                    timestamp,
                ]
            )
    event_id = "65000000-0000-0000-0000-000000000001"
    event = {
        "eventId": event_id,
        "eventType": "config.revision-published.v1",
        "schemaVersion": 1,
        "occurredAt": GENERATED_AT,
        "organizationId": ORGANIZATION_ID,
        "projectId": PROJECT_ID,
        "environmentId": DEVELOPMENT_ID,
        "revision": 1,
        "snapshotChecksum": checksum,
        "traceId": "66000000-0000-0000-0000-000000000001",
    }
    sql = f"""-- GENERATED by eng/generate_demo_seed.py. Do not edit by hand.
-- Every identity and configuration value is fictional and deterministic.

BEGIN;

INSERT INTO organizations (id, slug, name, status, version, created_at, updated_at)
VALUES ({sql_string(ORGANIZATION_ID)}, 'northstar-commerce', 'Northstar Commerce', 'ACTIVE', 0,
        {timestamp}, {timestamp})
ON CONFLICT (id) DO NOTHING;

INSERT INTO organization_memberships
    (id, organization_id, oidc_issuer, oidc_subject, role, created_at, updated_at)
VALUES
    ('20000000-0000-0000-0000-000000000001', {sql_string(ORGANIZATION_ID)},
     'http://localhost:8081/realms/launchforge', '30000000-0000-0000-0000-000000000001',
     'OWNER', {timestamp}, {timestamp}),
    ('20000000-0000-0000-0000-000000000002', {sql_string(ORGANIZATION_ID)},
     'http://localhost:8081/realms/launchforge', '30000000-0000-0000-0000-000000000002',
     'ADMIN', {timestamp}, {timestamp}),
    ('20000000-0000-0000-0000-000000000003', {sql_string(ORGANIZATION_ID)},
     'http://localhost:8081/realms/launchforge', '30000000-0000-0000-0000-000000000003',
     'DEVELOPER', {timestamp}, {timestamp}),
    ('20000000-0000-0000-0000-000000000004', {sql_string(ORGANIZATION_ID)},
     'http://localhost:8081/realms/launchforge', '30000000-0000-0000-0000-000000000004',
     'VIEWER', {timestamp}, {timestamp})
ON CONFLICT (id) DO NOTHING;

INSERT INTO projects
    (id, organization_id, project_key, name, description, status, version, created_at, updated_at)
VALUES ({sql_string(PROJECT_ID)}, {sql_string(ORGANIZATION_ID)}, 'storefront', 'Storefront',
        'Deterministic fictional Northstar Commerce demo.', 'ACTIVE', 0,
        {timestamp}, {timestamp})
ON CONFLICT (id) DO NOTHING;

INSERT INTO environments
    (id, organization_id, project_id, environment_key, name, kind, status,
     current_revision, version, created_at, updated_at)
VALUES
    {tuple_rows(environment_rows)}
ON CONFLICT (id) DO NOTHING;

INSERT INTO flags
    (id, organization_id, project_id, flag_key, name, flag_type, client_visible, status,
     version, created_at, updated_at)
VALUES
    {tuple_rows(flag_rows)}
ON CONFLICT (id) DO NOTHING;

INSERT INTO flag_variations
    (id, organization_id, project_id, flag_id, variation_key, name, canonical_value, position)
VALUES
    {tuple_rows(variation_rows)}
ON CONFLICT (id) DO NOTHING;

INSERT INTO flag_environment_configs
    (organization_id, project_id, environment_id, flag_id, enabled,
     fallthrough_variation_id, off_variation_id, rollout_salt, rules, rollout,
     change_summary, version, updated_at)
VALUES
    {tuple_rows(config_rows)}
ON CONFLICT (environment_id, flag_id) DO NOTHING;

INSERT INTO environment_revisions
    (organization_id, project_id, environment_id, revision, schema_version,
     snapshot_json, canonical_snapshot, checksum_sha256, human_reason,
     actor_issuer, actor_subject, created_at)
VALUES ({sql_string(ORGANIZATION_ID)}, {sql_string(PROJECT_ID)}, {sql_string(DEVELOPMENT_ID)}, 1, 1,
        {sql_json(snapshot)}, {sql_string(canonical_snapshot)}, {sql_string(checksum)},
        'Establish deterministic fictional demo baseline.', 'local-demo', 'northstar-seed',
        {timestamp})
ON CONFLICT (environment_id, revision) DO NOTHING;

INSERT INTO browser_client_keys
    (id, organization_id, project_id, environment_id, name, client_key, fingerprint,
     allowed_origins, status, created_at, created_by_issuer, created_by_subject)
VALUES ('67000000-0000-0000-0000-000000000001', {sql_string(ORGANIZATION_ID)},
        {sql_string(PROJECT_ID)}, {sql_string(DEVELOPMENT_ID)}, 'Northstar browser demo',
        'lf_client_' || {sql_string(BROWSER_KEY_SUFFIX)}, 'public:northstar-demo',
        '["http://127.0.0.1:5174","http://localhost:5174"]'::jsonb, 'ACTIVE',
        {timestamp}, 'local-demo', 'northstar-seed')
ON CONFLICT (id) DO NOTHING;

INSERT INTO audit_events
    (id, organization_id, actor_issuer, actor_subject, action, target_type, target_id,
     reason_code, correlation_id, created_at, project_id, environment_id, safe_summary,
     human_reason, from_revision, to_revision)
VALUES ('68000000-0000-0000-0000-000000000001', {sql_string(ORGANIZATION_ID)},
        'local-demo', 'northstar-seed', 'ENVIRONMENT_PUBLISHED', 'ENVIRONMENT',
        {sql_string(DEVELOPMENT_ID)}, 'SUCCESS',
        '69000000-0000-0000-0000-000000000001', {timestamp}, {sql_string(PROJECT_ID)},
        {sql_string(DEVELOPMENT_ID)}, 'Published deterministic fictional demo revision 1.',
        'Establish deterministic fictional demo baseline.', NULL, 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO outbox_events
    (id, organization_id, aggregate_type, aggregate_id, aggregate_revision,
     event_type, schema_version, payload, status, attempt_count, available_at, created_at)
VALUES ({sql_string(event_id)}, {sql_string(ORGANIZATION_ID)}, 'ENVIRONMENT',
        {sql_string(DEVELOPMENT_ID)}, 1, 'config.revision-published.v1', 1,
        {sql_json(event)}, 'PENDING', 0, {timestamp}, {timestamp})
ON CONFLICT (id) DO NOTHING;

COMMIT;
"""
    return sql


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="fail if the committed seed is stale")
    arguments = parser.parse_args()
    expected = render_sql()
    if arguments.check:
        actual = OUTPUT.read_text(encoding="utf-8") if OUTPUT.exists() else ""
        if actual != expected:
            print(f"{OUTPUT.relative_to(ROOT)} is stale; run {Path(__file__).name}", file=sys.stderr)
            return 1
        print(f"Verified {OUTPUT.relative_to(ROOT)}")
        return 0
    OUTPUT.write_text(expected, encoding="utf-8", newline="\n")
    print(f"Wrote {OUTPUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
