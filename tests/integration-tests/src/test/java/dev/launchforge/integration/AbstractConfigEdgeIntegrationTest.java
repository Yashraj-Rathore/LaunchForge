package dev.launchforge.integration;

import dev.launchforge.configedge.LaunchForgeConfigEdgeApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@ActiveProfiles("test")
@SpringBootTest(
    classes = LaunchForgeConfigEdgeApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.main.web-application-type=reactive",
      "spring.flyway.enabled=true",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.security.oauth2.client.autoconfigure.reactive."
          + "ReactiveOAuth2ClientAutoConfiguration,"
          + "org.springframework.boot.security.oauth2.client.autoconfigure.reactive."
          + "ReactiveOAuth2ClientWebSecurityAutoConfiguration,"
          + "org.springframework.boot.security.autoconfigure.web.reactive."
          + "ReactiveWebSecurityAutoConfiguration,"
          + "org.springframework.boot.security.autoconfigure.actuate.web.reactive."
          + "ReactiveManagementWebSecurityAutoConfiguration",
      "launchforge.config-edge.revision-poll-interval=25ms",
      "launchforge.config-edge.heartbeat-interval=100ms",
      "launchforge.config-edge.maximum-connections=4",
      "launchforge.config-edge.maximum-connections-per-key=1",
      "launchforge.config-edge.redis-enabled=false"
    })
abstract class AbstractConfigEdgeIntegrationTest {
  static final String TEST_PEPPER = "integration-test-pepper-value-with-more-than-thirty-two-bytes";

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18.4-bookworm")
          .withDatabaseName("launchforge_edge_test")
          .withUsername("launchforge_edge_test")
          .withPassword("integration-test-only");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void edgeProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("launchforge.sdk-keys.peppers.v1", () -> TEST_PEPPER);
  }
}
