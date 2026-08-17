package dev.launchforge.application.controlplane;

import dev.launchforge.domain.controlplane.EnvironmentId;

public record RevisionDiagnostics(
    EnvironmentId environmentId,
    long databaseRevision,
    long pendingOutboxCount,
    long failedOutboxCount,
    long oldestPendingAgeMillis) {}
