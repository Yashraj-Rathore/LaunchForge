package dev.launchforge.migrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/** Applies the forward-only database migrations and then terminates. */
@SpringBootApplication
public class MigratorApplication {
  public static void main(String[] args) {
    SpringApplication application = new SpringApplication(MigratorApplication.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    try (ConfigurableApplicationContext context = application.run(args)) {
      System.exit(SpringApplication.exit(context));
    }
  }
}
