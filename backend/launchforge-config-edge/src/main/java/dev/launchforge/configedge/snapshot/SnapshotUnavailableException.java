package dev.launchforge.configedge.snapshot;

public final class SnapshotUnavailableException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public SnapshotUnavailableException() {
    super("No safe published snapshot is available");
  }

  public SnapshotUnavailableException(Throwable cause) {
    super("No safe published snapshot is available", cause);
  }
}
