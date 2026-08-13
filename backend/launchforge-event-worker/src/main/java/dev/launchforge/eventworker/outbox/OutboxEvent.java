package dev.launchforge.eventworker.outbox;

import java.util.UUID;

public record OutboxEvent(
    UUID id, UUID environmentId, long revision, String payload, int attempt) {}
