# 23 - M10 Reliability and Performance Report

## 1. Scope and conclusion

This report records LF-1001 through LF-1006 evidence from 2026-08-17. M10 supplies opt-in
OpenTelemetry, bounded correlation/metrics/diagnostics, local observability assets, a reproducible
JMH evaluator harness, k6 workload definitions, durable Java SDK last-known-good storage, and
executed failure drills.

The JMH numbers below are measurements from one developer machine. They are not production
capacity, an SLO, or a Java-versus-JavaScript comparison. No HTTP, SSE concurrency, or
publish-convergence capacity claim is made because the k6 suites were configuration-validated but
not run against a controlled representative deployment.

## 2. Issue evidence

| Issue | Delivered evidence |
|---|---|
| LF-1001 | Spring Boot OpenTelemetry starter in all processes; opt-in OTLP tracing; W3C HTTP/Kafka propagation; bounded ingress correlation echoed in responses/problems; ECS structured logs |
| LF-1002 | Prometheus registry, fixed-cardinality management/edge/outbox/projection meters, authenticated revision diagnostic, four alert examples, and a provisioned Grafana reliability dashboard |
| LF-1003 | Isolated JMH 1.37 module with GC allocation profiling and five evaluator scenarios |
| LF-1004 | k6 1.7.1 snapshot, SSE reconnect, and publish-convergence scripts plus a load-report template |
| LF-1005 | Optional atomic local-file Java SDK LKG with complete validation, stale-revision protection, and corrupt-file fallback |
| LF-1006 | Executed PostgreSQL/Kafka/Redis/edge/SDK drills and completed runbook set |

## 3. JMH evaluator benchmark

### Reproducibility metadata

- Implementation Git SHA: `993980f2ee70b134c47d1590e07bd3cf9a86cd94`
- JMH: 1.37
- Java: Eclipse Temurin OpenJDK 25.0.4+7 LTS, 64-bit Server VM
- OS/runtime: Microsoft Windows kernel 10.0.22631, x64
- CPU: 11th Gen Intel Core i7-11700 at 2.50 GHz; 16 logical processors
- Physical memory: 17,009,291,264 bytes
- Threads: 1
- Forks: 1
- Warmup: 3 iterations of 1 second
- Measurement: 5 iterations of 1 second
- Profiler: JMH `gc`
- Raw artifact: `tests/performance/results/2026-08-17/jmh.json`

Build:

    .\mvnw.cmd -pl tests/performance -am spotless:apply package -DskipTests

Execution:

    java -jar tests/performance/target/launchforge-benchmarks.jar -f 1 -wi 3 -i 5 -w 1s -r 1s -prof gc -rf json -rff tests/performance/results/2026-08-17/jmh.json

### Recorded results

Scores are mean throughput with JMH's reported 99.9% confidence interval. Allocation is normalized
bytes per operation.

| Scenario | Mean ops/s | Error ops/s | Bytes/op |
|---|---:|---:|---:|
| Boolean default variation | 34,634,586.748 | 968,309.129 | 80.000 |
| First matching rule | 26,235,311.991 | 3,379,756.692 | 96.000 |
| Matching rule at position 100 | 884,441.528 | 96,038.906 | 96.008 |
| Percentage rollout | 3,733,354.443 | 247,518.082 | 568.002 |
| JSON variation | 35,365,872.595 | 3,973,497.226 | 80.000 |

Limitations:

- one fork and short one-second iterations favor fast local feedback over publication-grade
  statistical confidence;
- the benchmark isolates pure evaluation and intentionally excludes parsing, I/O, refresh,
  telemetry, application logic, and network effects;
- CPU power state, background activity, thermal state, and Docker workloads were not controlled;
- the 100-rule case exercises the maximum permitted rule count on one flag, not a full 2,000-flag
  snapshot or concurrent snapshot swaps;
- JDK 25 reports that JMH 1.37 uses a terminally deprecated Unsafe lookup and experimental compiler
  blackholes, which is another reason not to generalize the figures;
- compare future results only with the same source, JDK, blackhole mode, JVM options, forks,
  iteration lengths, and host controls.

## 4. k6 workload status

Pinned image: `grafana/k6:1.7.1@sha256:4fd3a694926b064d3491d9b02b01cde886583c4931f1223816e3d9a7bdfa7e0f`.

The three scripts passed `k6 inspect` in the pinned container:

- `snapshot-load.js`: conditional/full reads labeled for a separately prepared Redis-warm or
  PostgreSQL-fallback source profile, with revision/checksum integrity checks;
- `sse-reconnect.js`: configurable connection hold/reconnect pressure and explicit 429 handling;
- `publish-convergence.js`: authenticated PostgreSQL, Redis-materialization, and actual edge
  snapshot revision timing from a caller-supplied commit timestamp.

Validation command shape:

    docker run --rm -v "${PWD}:/work" -w /work grafana/k6:1.7.1@sha256:4fd3a694926b064d3491d9b02b01cde886583c4931f1223816e3d9a7bdfa7e0f inspect tests/performance/k6/<script>.js

All three inspections exited 0. No requests were sent. The native k6 HTTP client buffers SSE, so
the reconnect workload treats a configured hold timeout as an accepted long-lived connection; it
does not validate individual SSE frames. Event semantics remain covered by SDK/integration tests.
Use an SSE-capable extension only after reviewing and pinning its supply-chain artifact.

## 5. Failure-drill evidence

The exact focused command and outcome table are recorded in
`docs/18_FAILURE_MODES_RUNBOOKS.md`. At implementation SHA `993980f`, the final run completed
with 12 passing `ControlPlanePostgresIT` cases and one passing `DistributionPipelineIT` drill.
It exercised tenant-scoped revision diagnosis, rollback, durable outbox behavior, invalid envelope
failure, duplicate safety, Kafka pause/recovery, projector pause/recovery, Redis flush/rebuild and
outage fallback, two edges, edge restart, and Java SDK LKG. No config/revision loss occurred.

The test-class durations are not recovery-time measurements. Production detection and recovery
time remain deployment-specific.

## 6. Observability asset validation

The following validations completed successfully:

- Compose interpolation/configuration for the `observability` profile with ephemeral validation
  placeholders;
- Prometheus `promtool check config`: one rule file and four valid rules;
- OpenTelemetry Collector `validate` against the committed configuration;
- an isolated Compose start reached ready/healthy state for the collector, Prometheus, and Grafana,
  then removed its containers, network, and volumes cleanly;
- unit/HTTP tests for bounded metric tags and correlation echo/reuse;
- the real tenant test proving cross-organization diagnostic access returns `404`.

Grafana background plugin preinstallation is disabled so the provisioned local dashboard does not
depend on mutable downloads. The local collector intentionally uses the debug trace exporter.
Selecting and securing a durable trace backend belongs to the deployment environment; it is not
silently introduced here.

## 7. Final validation

The final Prompt 11 tree passed these applicable repository gates on 2026-08-17:

- `.\mvnw.cmd --batch-mode --no-transfer-progress verify`: all 13 reactor modules succeeded;
- `.\mvnw.cmd --batch-mode --no-transfer-progress -pl tests/integration-tests -am verify
  -Pintegration`: 26 integration cases passed with no failures, errors, or skips;
- locked Node 24.19.0/pnpm 11.21.0 container run: formatting, lint, type-check, 21 unit
  tests, and all production builds passed;
- `python eng/validate_docs.py`: 78 backlog issues and 16 Codex prompts passed;
- all committed JSON templates, contracts, the Grafana dashboard, and the raw JMH artifact parsed
  successfully;
- all Compose profiles resolved, and the three pinned k6 inspections passed without sending
  requests.

## 8. Follow-up boundary

No LF-1001 through LF-1006 correctness gap remains. Before making any public capacity statement,
run the committed k6 workloads on a controlled multi-instance deployment and complete
`tests/performance/load-report-template.md`. M11 owns container images and Helm deployment;
M12 owns release-performance gates and supply-chain automation.
