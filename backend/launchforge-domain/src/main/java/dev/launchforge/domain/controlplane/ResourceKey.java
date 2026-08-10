package dev.launchforge.domain.controlplane;

import java.util.Objects;
import java.util.regex.Pattern;

public record ResourceKey(String value) {
  private static final Pattern FORMAT = Pattern.compile("^[a-z][a-z0-9._-]{0,63}$");

  public ResourceKey {
    Objects.requireNonNull(value, "value");
    if (!FORMAT.matcher(value).matches()) {
      throw new ControlPlaneRuleViolationException("Resource key is not canonical");
    }
  }
}
