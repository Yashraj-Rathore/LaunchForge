package dev.launchforge.domain.organization;

import java.util.Objects;
import java.util.regex.Pattern;

public record OrganizationSlug(String value) {
  private static final Pattern VALID_SLUG =
      Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");

  public OrganizationSlug {
    Objects.requireNonNull(value, "value");
    if (!VALID_SLUG.matcher(value).matches()) {
      throw new DomainRuleViolationException("Organization slug is not canonical");
    }
  }
}
