package dev.launchforge.domain.organization;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

public record OidcIdentity(String issuer, String subject) {
  private static final int MAX_ISSUER_LENGTH = 2048;
  private static final int MAX_SUBJECT_LENGTH = 255;

  public OidcIdentity {
    issuer = requireCanonicalText(issuer, "issuer", MAX_ISSUER_LENGTH);
    subject = requireCanonicalText(subject, "subject", MAX_SUBJECT_LENGTH);

    URI issuerUri;
    try {
      issuerUri = URI.create(issuer);
    } catch (IllegalArgumentException exception) {
      throw new DomainRuleViolationException("OIDC issuer must be a valid absolute URI");
    }
    String scheme = issuerUri.getScheme();
    if (!issuerUri.isAbsolute()
        || scheme == null
        || !(scheme.toLowerCase(Locale.ROOT).equals("https")
            || scheme.toLowerCase(Locale.ROOT).equals("http"))
        || issuerUri.getHost() == null
        || issuerUri.getQuery() != null
        || issuerUri.getFragment() != null) {
      throw new DomainRuleViolationException("OIDC issuer must be an HTTP(S) issuer URI");
    }
  }

  private static String requireCanonicalText(String value, String field, int maximumLength) {
    Objects.requireNonNull(value, field);
    if (value.isBlank() || !value.equals(value.strip()) || value.length() > maximumLength) {
      throw new DomainRuleViolationException(field + " is blank, non-canonical, or too long");
    }
    return value;
  }
}
