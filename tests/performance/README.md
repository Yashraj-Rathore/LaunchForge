# LaunchForge performance harness

The harness separates microbenchmark mechanics from capacity claims. JMH measures the pure local
Java evaluator, while k6 exercises HTTP snapshots, SSE reconnects, and post-publication
convergence against a deliberately selected deployment.

Build the evaluator suite:

    .\mvnw.cmd -pl tests/performance -am package -DskipTests

Run it with allocation profiling and machine-readable output:

    java -jar tests/performance/target/launchforge-benchmarks.jar -prof gc -rf json -rff tests/performance/results/<date>/jmh.json

The committed result report records the Git SHA, Java/runtime and host metadata, exact command,
warmup/fork settings, throughput, allocation rate, and limitations. Results from a laptop are
evidence about that machine only and are not production SLOs.

Validate k6 scripts without sending traffic:

    docker run --rm -v "${PWD}:/work" -w /work grafana/k6:1.7.1@sha256:4fd3a694926b064d3491d9b02b01cde886583c4931f1223816e3d9a7bdfa7e0f inspect tests/performance/k6/snapshot-load.js
    docker run --rm -v "${PWD}:/work" -w /work grafana/k6:1.7.1@sha256:4fd3a694926b064d3491d9b02b01cde886583c4931f1223816e3d9a7bdfa7e0f inspect tests/performance/k6/sse-reconnect.js
    docker run --rm -v "${PWD}:/work" -w /work grafana/k6:1.7.1@sha256:4fd3a694926b064d3491d9b02b01cde886583c4931f1223816e3d9a7bdfa7e0f inspect tests/performance/k6/publish-convergence.js

Each script documents its required environment variables. Set capacity thresholds only after the
deployment shape, workload, dataset, and service-level objective are approved.

For snapshot-source experiments, run against a dedicated disposable environment. Use
`SOURCE_PROFILE=redis_warm` after prewarming Redis. Use `SOURCE_PROFILE=postgres_fallback` only
after deliberately making Redis unavailable under the Redis failure runbook; the value labels the
workload and does not itself mutate Redis. Never flush a shared environment from a load script.
