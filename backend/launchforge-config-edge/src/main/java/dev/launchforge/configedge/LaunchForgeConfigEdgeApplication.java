package dev.launchforge.configedge;

import dev.launchforge.configedge.configuration.ConfigEdgeProperties;
import dev.launchforge.configedge.configuration.SdkKeyPepperProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ConfigEdgeProperties.class, SdkKeyPepperProperties.class})
public class LaunchForgeConfigEdgeApplication {
  public static void main(String[] args) {
    SpringApplication.run(LaunchForgeConfigEdgeApplication.class, args);
  }
}
