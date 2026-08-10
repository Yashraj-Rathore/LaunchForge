# Codex Prompt 05 - Config Edge and Streaming

Implement **LF-0401 through LF-0406 only**.

Build:

- separate Spring Boot WebFlux Config Edge;
- scoped server SDK-key authentication with one-time secret/hash model;
- authoritative PostgreSQL-backed snapshot endpoint;
- ETag/304/revision/checksum contract;
- authenticated SSE revision notification stream;
- Java SDK stream support with exponential backoff + jitter;
- polling fallback;
- edge/SDK failure tests;
- reproducible live-update Java demo.

SSE carries revision notifications only. The SDK then fetches the authoritative snapshot.

Do not add Kafka or Redis yet. Prove the architecture works from PostgreSQL first.

Run tests/E2E, update docs/status/changelog, and stop.
