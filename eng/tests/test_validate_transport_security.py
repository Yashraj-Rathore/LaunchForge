import unittest

from eng.validate_transport_security import (
    ContractViolation,
    validate_compose_secure,
    validate_helm_local,
    validate_helm_secure,
)


class TransportSecurityContractTest(unittest.TestCase):
    def test_secure_helm_requires_secret_backed_kafka_identity(self) -> None:
        with self.assertRaisesRegex(ContractViolation, "LAUNCHFORGE_REDIS_SSL"):
            validate_helm_secure("")

    def test_local_helm_rejects_kafka_secret_mounts(self) -> None:
        document = (
            'value: "PLAINTEXT"\n' * 2
            + "name: LAUNCHFORGE_REDIS_SSL_ENABLED\n" * 3
            + 'value: "false"\n' * 5
            + "SPRING_KAFKA_SSL_BUNDLE"
        )
        with self.assertRaisesRegex(ContractViolation, "forbidden"):
            validate_helm_local(document)

    def test_secure_compose_requires_tls_only_redis_healthcheck(self) -> None:
        with self.assertRaisesRegex(ContractViolation, "SASL_SSL"):
            validate_compose_secure("")


if __name__ == "__main__":
    unittest.main()
