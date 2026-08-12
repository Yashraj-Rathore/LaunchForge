package dev.launchforge.domain.sdkkey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.launchforge.domain.controlplane.EnvironmentId;
import dev.launchforge.domain.controlplane.ProjectId;
import dev.launchforge.domain.organization.OrganizationId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class BrowserClientKeyTest {
  private static final String PUBLIC_KEY = "lf_client_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

  @Test
  void acceptsExactHttpsAndLocalDevelopmentOrigins() {
    BrowserClientKey key = key(List.of("https://shop.example", "http://localhost:5173"));
    assertEquals(2, key.allowedOrigins().size());
  }

  @Test
  void rejectsWildcardsPathsCredentialsAndNonLocalHttp() {
    assertThrows(IllegalArgumentException.class, () -> key(List.of("https://*.example")));
    assertThrows(IllegalArgumentException.class, () -> key(List.of("https://shop.example/path")));
    assertThrows(IllegalArgumentException.class, () -> key(List.of("https://user@shop.example")));
    assertThrows(IllegalArgumentException.class, () -> key(List.of("http://shop.example")));
  }

  private static BrowserClientKey key(List<String> origins) {
    Instant now = Instant.parse("2026-08-12T12:00:00Z");
    return new BrowserClientKey(
        SdkKeyId.random(),
        OrganizationId.random(),
        ProjectId.random(),
        EnvironmentId.random(),
        "Storefront browser",
        PUBLIC_KEY,
        "0123456789abcdef01234567",
        origins,
        SdkKeyStatus.ACTIVE,
        null,
        now,
        null,
        null);
  }
}
