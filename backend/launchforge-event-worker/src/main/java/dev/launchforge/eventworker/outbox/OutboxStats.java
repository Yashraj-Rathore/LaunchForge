package dev.launchforge.eventworker.outbox;

public record OutboxStats(long pendingCount, double oldestPendingAgeSeconds) {}
