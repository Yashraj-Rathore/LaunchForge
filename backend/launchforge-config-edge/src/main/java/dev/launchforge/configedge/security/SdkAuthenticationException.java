package dev.launchforge.configedge.security;

public final class SdkAuthenticationException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final boolean forbidden;

  private SdkAuthenticationException(boolean forbidden) {
    super(forbidden ? "SDK key scope is inactive" : "SDK key is invalid");
    this.forbidden = forbidden;
  }

  public static SdkAuthenticationException unauthorized() {
    return new SdkAuthenticationException(false);
  }

  public static SdkAuthenticationException forbidden() {
    return new SdkAuthenticationException(true);
  }

  public boolean isForbidden() {
    return forbidden;
  }
}
