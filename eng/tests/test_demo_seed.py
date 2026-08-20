import hashlib
import unittest

from eng import generate_demo_seed


class DemoSeedTest(unittest.TestCase):
    def test_committed_seed_matches_generator(self):
        self.assertEqual(
            generate_demo_seed.render_sql(),
            generate_demo_seed.OUTPUT.read_text(encoding="utf-8"),
        )

    def test_snapshot_is_checksum_valid_and_contains_the_portfolio_flag_set(self):
        snapshot, canonical, checksum = generate_demo_seed.development_snapshot()
        projection = dict(snapshot)
        projection.pop("checksum")

        self.assertEqual(
            hashlib.sha256(generate_demo_seed.canonical_json(projection).encode("utf-8")).hexdigest(),
            checksum,
        )
        self.assertEqual(generate_demo_seed.canonical_json(snapshot), canonical)
        self.assertEqual(
            {
                "new-checkout",
                "search-ranking",
                "recommendation-card",
                "fraud-review-threshold",
            },
            set(snapshot["flags"]),
        )
        self.assertFalse(snapshot["flags"]["fraud-review-threshold"]["clientVisible"])
        self.assertEqual(
            [80],
            snapshot["flags"]["fraud-review-threshold"]["rules"][0]["conditions"][0][
                "values"
            ],
        )
        self.assertEqual(
            [10_000, 90_000],
            [
                allocation["weight"]
                for allocation in snapshot["flags"]["new-checkout"]["rollout"]["weights"]
            ],
        )

    def test_demo_rollout_subjects_make_the_ten_to_fifty_percent_change_visible(self):
        def bucket(subject: str) -> int:
            material = f"new-checkout\nstable_salt_1234\n{subject}".encode()
            prefix = int.from_bytes(hashlib.sha256(material).digest()[:8], "big", signed=False)
            return prefix % 100_000

        internal_bucket = bucket("demo-26")
        free_bucket = bucket("demo-60157")

        self.assertGreaterEqual(internal_bucket, 10_000)
        self.assertLess(internal_bucket, 50_000)
        self.assertEqual(50_000, free_bucket)

    def test_seed_is_explicitly_fictional_and_has_no_server_credential(self):
        sql = generate_demo_seed.render_sql()

        self.assertIn("Northstar Commerce", sql)
        self.assertIn("development", sql)
        self.assertIn("staging", sql)
        self.assertIn("production", sql)
        self.assertNotIn("INSERT INTO sdk_keys", sql)
        self.assertEqual(32, len(generate_demo_seed.BROWSER_KEY_SUFFIX))


if __name__ == "__main__":
    unittest.main()
