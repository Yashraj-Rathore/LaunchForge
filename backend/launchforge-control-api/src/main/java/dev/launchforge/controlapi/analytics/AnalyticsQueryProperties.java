package dev.launchforge.controlapi.analytics;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("launchforge.analytics.query")
public record AnalyticsQueryProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("http://localhost:58123") URI clickHouseUrl,
    @DefaultValue("launchforge") String clickHouseUser,
    @DefaultValue("") String clickHousePassword,
    @DefaultValue("2s") Duration connectTimeout,
    @DefaultValue("3s") Duration requestTimeout,
    @DefaultValue("8") int maximumConcurrentQueries,
    @DefaultValue("31d") Duration maximumRange,
    @DefaultValue("1000") int maximumRows) {
  public AnalyticsQueryProperties {
    if (clickHouseUrl == null
        || !clickHouseUrl.isAbsolute()
        || !("http".equalsIgnoreCase(clickHouseUrl.getScheme())
            || "https".equalsIgnoreCase(clickHouseUrl.getScheme()))
        || clickHouseUrl.getHost() == null
        || clickHouseUrl.getRawUserInfo() != null
        || clickHouseUrl.getRawQuery() != null
        || clickHouseUrl.getRawFragment() != null) {
      throw new IllegalArgumentException("clickHouseUrl is invalid");
    }
    if (clickHouseUser == null || clickHouseUser.isBlank() || clickHouseUser.length() > 128) {
      throw new IllegalArgumentException("clickHouseUser is invalid");
    }
    if (clickHousePassword == null || clickHousePassword.length() > 4096) {
      throw new IllegalArgumentException("clickHousePassword is invalid");
    }
    requireDuration(
        connectTimeout, "connectTimeout", Duration.ofMillis(100), Duration.ofSeconds(30));
    requireDuration(
        requestTimeout, "requestTimeout", Duration.ofMillis(100), Duration.ofSeconds(30));
    requireDuration(maximumRange, "maximumRange", Duration.ofHours(1), Duration.ofDays(366));
    if (maximumConcurrentQueries < 1 || maximumConcurrentQueries > 1000) {
      throw new IllegalArgumentException("maximumConcurrentQueries is invalid");
    }
    if (maximumRows < 1 || maximumRows > 10_000) {
      throw new IllegalArgumentException("maximumRows is invalid");
    }
  }

  private static void requireDuration(
      Duration value, String name, Duration minimum, Duration maximum) {
    if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
      throw new IllegalArgumentException(name + " is invalid");
    }
  }
}
