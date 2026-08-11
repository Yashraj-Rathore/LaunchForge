package dev.launchforge.controlapi.configuration;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("launchforge.sdk-keys")
public record SdkKeySecurityProperties(String currentPepperVersion, Map<String, String> peppers) {
  public SdkKeySecurityProperties {
    if (currentPepperVersion == null || !currentPepperVersion.matches("[A-Za-z0-9._-]{1,32}")) {
      throw new IllegalArgumentException("Current SDK key pepper version is invalid");
    }
    peppers = Map.copyOf(Objects.requireNonNull(peppers, "peppers"));
    for (Map.Entry<String, String> entry : peppers.entrySet()) {
      if (!entry.getKey().matches("[A-Za-z0-9._-]{1,32}")
          || entry.getValue().getBytes(StandardCharsets.UTF_8).length < 32) {
        throw new IllegalArgumentException(
            "Every SDK key pepper must be versioned and at least 32 bytes");
      }
    }
    if (!peppers.containsKey(currentPepperVersion)) {
      throw new IllegalArgumentException("Current SDK key pepper is not configured");
    }
  }

  public String currentPepper() {
    return peppers.get(currentPepperVersion);
  }
}
