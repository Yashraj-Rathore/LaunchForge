package dev.launchforge.configedge.stream;

public final class ConnectionLimitExceededException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public ConnectionLimitExceededException() {
    super("SSE connection limit reached");
  }
}
