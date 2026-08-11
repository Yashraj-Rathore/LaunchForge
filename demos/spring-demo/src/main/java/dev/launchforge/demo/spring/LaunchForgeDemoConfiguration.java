package dev.launchforge.demo.spring;

import dev.launchforge.sdk.LaunchForgeClient;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class LaunchForgeDemoConfiguration {
  @Bean(destroyMethod = "close")
  LaunchForgeClient launchForgeClient(
      @Value("${launchforge.base-uri}") URI baseUri,
      @Value("${launchforge.sdk-key}") String sdkKey,
      @Value("${launchforge.bootstrap-timeout:5s}") Duration bootstrapTimeout,
      @Value("${launchforge.streaming:true}") boolean streaming) {
    return LaunchForgeClient.builder()
        .baseUri(baseUri)
        .sdkKey(sdkKey)
        .streaming(streaming)
        .blockingBootstrap(bootstrapTimeout)
        .build();
  }
}
