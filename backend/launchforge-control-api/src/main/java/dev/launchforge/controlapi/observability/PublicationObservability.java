package dev.launchforge.controlapi.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public final class PublicationObservability {
  private final MeterRegistry meterRegistry;
  private final ObservationRegistry observationRegistry;

  public PublicationObservability(
      MeterRegistry meterRegistry, ObservationRegistry observationRegistry) {
    this.meterRegistry = meterRegistry;
    this.observationRegistry = observationRegistry;
  }

  public <T> T observe(String operation, Supplier<T> action) {
    Timer.Sample sample = Timer.start(meterRegistry);
    Observation observation =
        Observation.createNotStarted("launchforge.management.publication", observationRegistry)
            .lowCardinalityKeyValue("operation", operation)
            .start();
    String outcome = "success";
    try {
      return action.get();
    } catch (RuntimeException exception) {
      outcome = "failure";
      observation.error(exception);
      throw exception;
    } finally {
      observation.lowCardinalityKeyValue("outcome", outcome);
      observation.stop();
      Counter.builder("launchforge.management.publication")
          .tag("operation", operation)
          .tag("outcome", outcome)
          .register(meterRegistry)
          .increment();
      sample.stop(
          Timer.builder("launchforge.management.publication.duration")
              .tag("operation", operation)
              .tag("outcome", outcome)
              .register(meterRegistry));
    }
  }
}
