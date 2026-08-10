package dev.launchforge.application.controlplane;

import dev.launchforge.domain.controlplane.EnvironmentId;
import java.time.Instant;

public record PublishedRevision(
    EnvironmentId environmentId,
    long revision,
    Long sourceRevision,
    String checksum,
    String canonicalSnapshot,
    String reason,
    String actorSubject,
    Instant createdAt) {}
