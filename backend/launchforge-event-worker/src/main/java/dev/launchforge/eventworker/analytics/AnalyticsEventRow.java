package dev.launchforge.eventworker.analytics;

import java.time.Instant;
import java.util.UUID;

public record AnalyticsEventRow(
    UUID eventId,
    Instant occurredAt,
    Instant receivedAt,
    UUID organizationId,
    UUID projectId,
    UUID environmentId,
    String projectKey,
    String environmentKey,
    String flagKey,
    String variationId,
    String reason,
    long revision,
    String source) {}
