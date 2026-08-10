package dev.launchforge.controlapi.web;

public final class PreconditionRequiredException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public PreconditionRequiredException() {
    super("A valid If-Match resource version is required");
  }
}
