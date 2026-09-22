# Implementation Prompt 09 - Optional Evaluation Analytics

Implement **LF-0801 through LF-0805 only**.

Before coding, re-confirm that optional analytics is still desired. If the repository owner has explicitly skipped M8, report that and do not implement it.

If proceeding:

- opt-in privacy-bounded evaluation event schema;
- batched analytics ingestion;
- ClickHouse optional local profile/schema;
- bounded aggregate query API/UI;
- failure isolation/backpressure/drop behavior.

Analytics must never be required for:

- local flag evaluation;
- snapshot fetch;
- SSE;
- publish;
- rollback.

Do not claim statistical experiment significance beyond implemented methodology.

Run failure-isolation tests, update docs/status/changelog, and stop.
