package dev.launchforge.sdk;

/** Indicates that remote snapshot bytes cannot be safely activated. */
public final class SnapshotValidationException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;

  public SnapshotValidationException(String message) {
    super(message);
  }

  SnapshotValidationException(String message, Throwable cause) {
    super(message, cause);
  }
}
