package dev.launchforge.eventworker.projection;

import java.util.UUID;

public record AuthoritativeSnapshot(
    UUID organizationId,
    UUID projectId,
    UUID environmentId,
    long revision,
    long currentRevision,
    int schemaVersion,
    String canonicalSnapshot,
    String checksum) {}
