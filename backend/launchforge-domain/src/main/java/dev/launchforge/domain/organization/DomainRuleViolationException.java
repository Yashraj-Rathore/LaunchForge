package dev.launchforge.domain.organization;

public final class DomainRuleViolationException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public DomainRuleViolationException(String message) {
    super(message);
  }
}
