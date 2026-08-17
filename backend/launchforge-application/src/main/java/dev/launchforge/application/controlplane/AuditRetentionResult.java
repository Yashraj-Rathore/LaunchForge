package dev.launchforge.application.controlplane;

import java.util.Objects;

public record AuditRetentionResult(AuditRetentionPreview preview, int deletedCount) {
  public AuditRetentionResult {
    Objects.requireNonNull(preview, "preview");
    if (deletedCount < 0) {
      throw new IllegalArgumentException("deletedCount cannot be negative");
    }
  }
}
