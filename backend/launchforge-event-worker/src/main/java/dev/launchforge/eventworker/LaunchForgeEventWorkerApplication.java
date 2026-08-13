package dev.launchforge.eventworker;

import dev.launchforge.eventworker.configuration.DistributionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(DistributionProperties.class)
public class LaunchForgeEventWorkerApplication {
  public static void main(String[] args) {
    SpringApplication.run(LaunchForgeEventWorkerApplication.class, args);
  }
}
