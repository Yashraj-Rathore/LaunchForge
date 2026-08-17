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

    docker run --rm -v "${PWD}:/work" -w /work grafana/k6:1.7.1 inspect tests/performance/k6/snapshot-load.js
    docker run --rm -v "${PWD}:/work" -w /work grafana/k6:1.7.1 inspect tests/performance/k6/sse-reconnect.js
    docker run --rm -v "${PWD}:/work" -w /work grafana/k6:1.7.1 inspect tests/performance/k6/publish-convergence.js

Each script documents its required environment variables. Set capacity thresholds only after the
deployment shape, workload, dataset, and service-level objective are approved.
