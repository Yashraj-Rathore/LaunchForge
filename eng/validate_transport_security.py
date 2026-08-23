#!/usr/bin/env python3
"""Validate rendered transport-security deployment contracts without parsing secret values."""

from __future__ import annotations

import argparse
import re
import sys


class ContractViolation(ValueError):
    """Raised when a rendered deployment weakens the required transport contract."""


def _require(document: str, token: str, minimum: int = 1) -> None:
    observed = document.count(token)
    if observed < minimum:
        raise ContractViolation(
            f"expected at least {minimum} occurrence(s) of {token!r}, found {observed}"
        )


def _forbid(document: str, token: str) -> None:
    if token in document:
        raise ContractViolation(f"forbidden transport-security token rendered: {token!r}")


def _require_secret_reference(document: str, env_name: str, secret_key: str) -> None:
    pattern = re.compile(
        rf"name:\s*{re.escape(env_name)}\s+"
        rf"valueFrom:\s+secretKeyRef:\s+name:\s*[^\n]+\s+"
        rf"key:\s*[\"']?{re.escape(secret_key)}[\"']?",
        re.MULTILINE,
    )
    matches = pattern.findall(document)
    if len(matches) < 2:
        raise ContractViolation(
            f"expected Config Edge and Event Worker secret references for {env_name}"
        )


def validate_helm_secure(document: str) -> None:
    _require(document, "name: LAUNCHFORGE_REDIS_SSL_ENABLED", 3)
    _require(document, "name: SPRING_DATA_REDIS_SSL_BUNDLE", 3)
    _require(document, "file:/var/run/secrets/launchforge/redis/ca.crt", 3)
    _require(document, 'value: "SASL_SSL"', 2)
    _require(document, "name: LAUNCHFORGE_KAFKA_SASL_ENABLED", 2)
    _require(document, "name: SPRING_KAFKA_SSL_BUNDLE", 2)
    _require(document, "file:/var/run/secrets/launchforge/kafka/ca.crt", 2)
    _require(document, 'key: "redis-tls-ca.crt"', 3)
    _require(document, 'key: "kafka-tls-ca.crt"', 2)
    _require_secret_reference(
        document, "LAUNCHFORGE_KAFKA_SASL_USERNAME", "kafka-sasl-username"
    )
    _require_secret_reference(
        document, "LAUNCHFORGE_KAFKA_SASL_PASSWORD", "kafka-sasl-password"
    )
    _forbid(document, 'value: "PLAINTEXT"')


def validate_helm_local(document: str) -> None:
    _require(document, 'value: "PLAINTEXT"', 2)
    _require(document, "name: LAUNCHFORGE_REDIS_SSL_ENABLED", 3)
    _require(document, 'value: "false"', 5)
    for token in (
        "LAUNCHFORGE_KAFKA_SASL_USERNAME",
        "LAUNCHFORGE_KAFKA_SASL_PASSWORD",
        "SPRING_KAFKA_SSL_BUNDLE",
        "SPRING_DATA_REDIS_SSL_BUNDLE",
        "/var/run/secrets/launchforge/kafka",
        "/var/run/secrets/launchforge/redis",
    ):
        _forbid(document, token)


def validate_compose_secure(document: str) -> None:
    _require(
        document,
        "INTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT,CLIENT:SASL_SSL,EXTERNAL:SASL_SSL",
    )
    _require(document, "KAFKA_SSL_KEYSTORE_LOCATION: /run/launchforge/kafka/server.p12")
    _require(document, "LAUNCHFORGE_KAFKA_SECURITY_PROTOCOL: SASL_SSL", 2)
    _require(document, 'LAUNCHFORGE_KAFKA_SASL_ENABLED: "true"', 2)
    _require(document, "SPRING_KAFKA_SSL_BUNDLE: kafka", 2)
    _require(document, "file:/run/launchforge/kafka/ca.crt", 2)
    _require(document, 'LAUNCHFORGE_REDIS_SSL_ENABLED: "true"', 3)
    _require(document, "SPRING_DATA_REDIS_SSL_BUNDLE: redis", 3)
    _require(document, "file:/run/launchforge/redis/ca.crt", 3)
    _require(document, 'LAUNCHFORGE_REDIS_TLS_ENABLED: "true"')
    _require(document, "redis-cli --tls --cacert /run/launchforge/redis/ca.crt")


VALIDATORS = {
    "helm-secure": validate_helm_secure,
    "helm-local": validate_helm_local,
    "compose-secure": validate_compose_secure,
}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=VALIDATORS)
    args = parser.parse_args()
    try:
        VALIDATORS[args.mode](sys.stdin.read())
    except ContractViolation as exception:
        print(f"transport-security contract failed: {exception}", file=sys.stderr)
        return 1
    print(f"transport-security contract passed: {args.mode}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
