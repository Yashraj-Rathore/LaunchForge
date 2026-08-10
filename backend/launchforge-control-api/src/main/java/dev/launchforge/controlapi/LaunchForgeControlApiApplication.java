package dev.launchforge.controlapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** LaunchForge management control-plane process. */
@SpringBootApplication(scanBasePackages = "dev.launchforge")
public class LaunchForgeControlApiApplication {
  public static void main(String[] args) {
    SpringApplication.run(LaunchForgeControlApiApplication.class, args);
  }
}
