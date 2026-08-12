package dev.launchforge.configedge.configuration;

import dev.launchforge.configedge.persistence.EdgeRepository;
import dev.launchforge.configedge.security.BrowserClientAuthenticationService;
import dev.launchforge.configedge.security.SdkAuthenticationService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EdgeConfiguration {
  @Bean
  Clock edgeClock() {
    return Clock.systemUTC();
  }

  @Bean
  SdkAuthenticationService sdkAuthenticationService(
      EdgeRepository repository, SdkKeyPepperProperties properties, Clock clock) {
    return new SdkAuthenticationService(repository, properties, clock);
  }

  @Bean
  BrowserClientAuthenticationService browserClientAuthenticationService(
      EdgeRepository repository, Clock clock) {
    return new BrowserClientAuthenticationService(repository, clock);
  }
}
