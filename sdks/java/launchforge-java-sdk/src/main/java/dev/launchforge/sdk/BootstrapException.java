package dev.launchforge.sdk;

/** Thrown only by explicitly blocking bootstrap when no valid initial snapshot arrives in time. */
public final class BootstrapException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  BootstrapException(String message) {
    super(message);
  }

  BootstrapException(String message, Throwable cause) {
    super(message, cause);
  }
}
