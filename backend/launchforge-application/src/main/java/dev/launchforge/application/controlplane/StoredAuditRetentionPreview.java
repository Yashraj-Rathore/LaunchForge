package dev.launchforge.application.controlplane;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record StoredAuditRetentionPreview(AuditRetentionPreview preview, List<UUID> candidateIds) {
  public StoredAuditRetentionPreview {
    Objects.requireNonNull(preview, "preview");
    candidateIds = List.copyOf(Objects.requireNonNull(candidateIds, "candidateIds"));
    if (candidateIds.size() != preview.candidateCount()) {
      throw new IllegalArgumentException("Preview candidate count does not match its ID set");
    }
  }
}
