package dev.launchforge.integration;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import dev.launchforge.controlapi.LaunchForgeControlApiApplication;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

@ActiveProfiles("test")
@SpringBootTest(
    classes = {
      LaunchForgeControlApiApplication.class,
      AbstractControlApiIntegrationTest.TestOidcClientConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.MOCK)
abstract class AbstractControlApiIntegrationTest {
  static final String ISSUER = "https://identity.example/realms/launchforge";

  protected MockMvc mockMvc;

  @BeforeEach
  void configureMockMvc(WebApplicationContext applicationContext) {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext).apply(springSecurity()).build();
  }

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18.4-bookworm")
          .withDatabaseName("launchforge_test")
          .withUsername("launchforge_test")
          .withPassword("integration-test-only");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.security.oauth2.client.registration.keycloak.client-id", () -> "test");
    registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri", () -> ISSUER);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestOidcClientConfiguration {
    @Bean
    ClientRegistrationRepository testClientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("test-client")
              .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("openid", "profile")
              .authorizationUri(ISSUER + "/protocol/openid-connect/auth")
              .tokenUri(ISSUER + "/protocol/openid-connect/token")
              .jwkSetUri(ISSUER + "/protocol/openid-connect/certs")
              .issuerUri(ISSUER)
              .userInfoUri(ISSUER + "/protocol/openid-connect/userinfo")
              .userNameAttributeName("sub")
              .clientName("Test OIDC")
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
