CREATE DATABASE IF NOT EXISTS launchforge;

CREATE TABLE IF NOT EXISTS launchforge.evaluation_events
(
    event_id UUID,
    occurred_at DateTime64(3, 'UTC'),
    received_at DateTime64(3, 'UTC'),
    organization_id UUID,
    project_id UUID,
    environment_id UUID,
    project_key LowCardinality(String),
    environment_key LowCardinality(String),
    flag_key LowCardinality(String),
    variation_id LowCardinality(String),
    reason LowCardinality(String),
    revision UInt64,
    source LowCardinality(String)
)
ENGINE = MergeTree
PARTITION BY toYYYYMM(occurred_at)
ORDER BY (organization_id, project_id, environment_id, flag_key, occurred_at, event_id)
TTL occurred_at + INTERVAL 90 DAY DELETE
SETTINGS ttl_only_drop_parts = 1;
