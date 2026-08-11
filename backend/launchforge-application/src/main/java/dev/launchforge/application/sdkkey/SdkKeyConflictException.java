package dev.launchforge.application.sdkkey;

public final class SdkKeyConflictException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public SdkKeyConflictException(String message) {
    super(message);
  }
}
