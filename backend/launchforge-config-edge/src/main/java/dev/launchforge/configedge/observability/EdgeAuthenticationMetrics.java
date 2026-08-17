package dev.launchforge.configedge.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public final class EdgeAuthenticationMetrics {
  private final MeterRegistry registry;

  public EdgeAuthenticationMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public void denied(String credentialClass, String route) {
    registry
        .counter(
            "launchforge.edge.authentication",
            "credential",
            credentialClass,
            "route",
            route,
            "outcome",
            "denied")
        .increment();
  }
}
