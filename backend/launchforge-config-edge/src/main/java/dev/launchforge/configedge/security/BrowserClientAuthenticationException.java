package dev.launchforge.configedge.security;

public final class BrowserClientAuthenticationException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final boolean forbidden;

  private BrowserClientAuthenticationException(boolean forbidden) {
    super(
        forbidden ? "Browser client origin or scope is inactive" : "Browser client key is invalid");
    this.forbidden = forbidden;
  }

  static BrowserClientAuthenticationException unauthorized() {
    return new BrowserClientAuthenticationException(false);
  }

  static BrowserClientAuthenticationException forbidden() {
    return new BrowserClientAuthenticationException(true);
  }

  boolean isForbidden() {
    return forbidden;
  }
}
