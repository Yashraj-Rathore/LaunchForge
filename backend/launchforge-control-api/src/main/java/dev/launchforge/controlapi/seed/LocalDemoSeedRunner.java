package dev.launchforge.controlapi.seed;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "launchforge.demo-seed.enabled", havingValue = "true")
public final class LocalDemoSeedRunner implements ApplicationRunner {
  private final DataSource dataSource;
  private final Resource seedResource;

  public LocalDemoSeedRunner(
      DataSource dataSource, @Value("${launchforge.demo-seed.location}") Resource seedResource) {
    this.dataSource = dataSource;
    this.seedResource = seedResource;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator(seedResource);
    populator.setContinueOnError(false);
    populator.execute(dataSource);
  }
}
