package dev.launchforge.configedge.configuration;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("launchforge.sdk-keys")
public record SdkKeyPepperProperties(Map<String, String> peppers) {
  public SdkKeyPepperProperties {
    peppers = Map.copyOf(Objects.requireNonNull(peppers, "peppers"));
    if (peppers.isEmpty()) {
      throw new IllegalArgumentException("At least one SDK key pepper must be configured");
    }
    for (Map.Entry<String, String> entry : peppers.entrySet()) {
      if (!entry.getKey().matches("[A-Za-z0-9._-]{1,32}")
          || entry.getValue().getBytes(StandardCharsets.UTF_8).length < 32) {
        throw new IllegalArgumentException(
            "Every SDK key pepper must be versioned and at least 32 bytes");
      }
    }
  }

  public Map<String, byte[]> bytesByVersion() {
    Map<String, byte[]> values = new LinkedHashMap<>();
    peppers.forEach(
        (version, pepper) -> values.put(version, pepper.getBytes(StandardCharsets.UTF_8)));
    return Map.copyOf(values);
  }
}
