package dev.launchforge.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.launchforge.application.organization.MembershipAdministrationService;
import dev.launchforge.domain.organization.MembershipId;
import dev.launchforge.domain.organization.OidcIdentity;
import dev.launchforge.domain.organization.OrganizationId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class ControlApiTenancyIdentityIT extends AbstractControlApiIntegrationTest {
  private static final UUID ORGANIZATION_A =
      UUID.fromString("10000000-0000-0000-0000-00000000000a");
  private static final UUID ORGANIZATION_B =
      UUID.fromString("10000000-0000-0000-0000-00000000000b");
  private static final UUID OWNER_A_MEMBERSHIP =
      UUID.fromString("20000000-0000-0000-0000-00000000000a");
  private static final UUID OWNER_B_MEMBERSHIP =
      UUID.fromString("20000000-0000-0000-0000-00000000000b");
  private static final UUID VIEWER_A_MEMBERSHIP =
      UUID.fromString("20000000-0000-0000-0000-00000000000c");
  private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MembershipAdministrationService membershipService;

  @BeforeEach
  void seedTenants() {
    jdbcTemplate.execute("TRUNCATE TABLE organizations CASCADE");

    insertOrganization(ORGANIZATION_A, "alpha-organization", "Alpha Organization");
    insertOrganization(ORGANIZATION_B, "beta-organization", "Beta Organization");
    insertMembership(OWNER_A_MEMBERSHIP, ORGANIZATION_A, "owner-a", "OWNER");
    insertMembership(VIEWER_A_MEMBERSHIP, ORGANIZATION_A, "viewer-a", "VIEWER");
    insertMembership(OWNER_B_MEMBERSHIP, ORGANIZATION_B, "owner-b", "OWNER");
  }

  @AfterEach
  void clearSessions() {
    jdbcTemplate.update("DELETE FROM launchforge_session_attributes");
    jdbcTemplate.update("DELETE FROM launchforge_session");
  }

  @Test
  void unauthenticatedApiIsRejectedAndAuthorizationRequestUsesCodePkceStateAndNonce()
      throws Exception {
    mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());

    String location =
        mockMvc
            .perform(get("/oauth2/authorization/keycloak"))
            .andExpect(status().isFound())
            .andExpect(header().exists("Location"))
            .andReturn()
            .getResponse()
            .getHeader("Location");

    assertTrue(location != null && location.contains("response_type=code"));
    assertTrue(location.contains("code_challenge="));
    assertTrue(location.contains("code_challenge_method=S256"));
    assertTrue(location.contains("state="));
    assertTrue(location.contains("nonce="));
  }

  @Test
  void authenticatedSessionReturnsOnlyServerDerivedOrganization() throws Exception {
    String response =
        mockMvc
            .perform(get("/api/v1/auth/me").with(operator("owner-a")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertTrue(response.contains("Alpha Organization"));
    assertFalse(response.contains("Beta Organization"));
    assertTrue(response.contains("OWNER"));
  }

  @Test
  void crossTenantReadAndWriteAreDeniedWithoutChangingTargetTenant() throws Exception {
    mockMvc
        .perform(get("/api/v1/organizations/{id}", ORGANIZATION_B).with(operator("owner-a")))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            post("/api/v1/organizations/{id}/members", ORGANIZATION_B)
                .with(operator("owner-a"))
                .with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"issuer":"https://identity.example/realms/launchforge",
                     "subject":"intruder","role":"VIEWER"}
                    """))
        .andExpect(status().isNotFound());

    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM organization_memberships WHERE organization_id = ?",
            Integer.class,
            ORGANIZATION_B));
  }

  @Test
  void browserSuppliedOrganizationFieldIsRejectedAndCsrfIsRequired() throws Exception {
    String body =
        """
        {"issuer":"https://identity.example/realms/launchforge",
         "subject":"new-member","role":"VIEWER",
         "organizationId":"10000000-0000-0000-0000-00000000000b"}
        """;

    mockMvc
        .perform(
            post("/api/v1/organizations/{id}/members", ORGANIZATION_A)
                .with(operator("owner-a"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/organizations/{id}/members", ORGANIZATION_A)
                .with(operator("owner-a"))
                .with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void viewerMemberManagementDenialIsAuditedWithoutSensitiveClaims() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/organizations/{id}/members", ORGANIZATION_A)
                .with(operator("viewer-a"))
                .with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"issuer":"https://identity.example/realms/launchforge",
                     "subject":"new-viewer","role":"VIEWER"}
                    """))
        .andExpect(status().isForbidden());

    MapRow audit =
        jdbcTemplate.queryForObject(
            """
            SELECT action, reason_code, actor_subject
              FROM audit_events
             WHERE organization_id = ?
            """,
            (resultSet, rowNumber) ->
                new MapRow(
                    resultSet.getString("action"),
                    resultSet.getString("reason_code"),
                    resultSet.getString("actor_subject")),
            ORGANIZATION_A);
    assertEquals("MEMBERSHIP_ADD_DENIED", audit.action());
    assertEquals("ROLE_POLICY_DENIED", audit.reason());
    assertEquals("viewer-a", audit.subject());
  }

  @Test
  void finalOwnerConstraintSurvivesConcurrentRemovalAttempts() throws Exception {
    UUID secondOwnerMembership = UUID.fromString("20000000-0000-0000-0000-00000000000d");
    insertMembership(secondOwnerMembership, ORGANIZATION_A, "second-owner-a", "OWNER");
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<Boolean> first =
          executor.submit(
              () ->
                  removeAfterSignal(
                      start, new OidcIdentity(ISSUER, "owner-a"), secondOwnerMembership));
      Future<Boolean> second =
          executor.submit(
              () ->
                  removeAfterSignal(
                      start, new OidcIdentity(ISSUER, "second-owner-a"), OWNER_A_MEMBERSHIP));
      start.countDown();
      assertTrue(first.get() ^ second.get());
    }

    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM organization_memberships
             WHERE organization_id = ? AND role = 'OWNER'
            """,
            Integer.class,
            ORGANIZATION_A));
  }

  @Test
  void expiredAbsoluteSessionIsRejected() throws Exception {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute(
        "dev.launchforge.security.absoluteSessionExpiryEpochMilli", System.currentTimeMillis() - 1);

    mockMvc
        .perform(get("/api/v1/auth/me").session(session).with(operator("owner-a")))
        .andExpect(status().isUnauthorized());
  }

  private boolean removeAfterSignal(
      CountDownLatch start, OidcIdentity actor, UUID targetMembershipId) throws Exception {
    start.await();
    try {
      membershipService.remove(
          actor, new OrganizationId(ORGANIZATION_A), new MembershipId(targetMembershipId));
      return true;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private void insertOrganization(UUID id, String slug, String name) {
    jdbcTemplate.update(
        """
        INSERT INTO organizations
          (id, slug, name, status, version, created_at, updated_at)
        VALUES (?, ?, ?, 'ACTIVE', 0, ?, ?)
        """,
        id,
        slug,
        name,
        Timestamp.from(NOW),
        Timestamp.from(NOW));
  }

  private void insertMembership(UUID id, UUID organizationId, String subject, String role) {
    jdbcTemplate.update(
        """
        INSERT INTO organization_memberships
          (id, organization_id, oidc_issuer, oidc_subject, role, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        organizationId,
        ISSUER,
        subject,
        role,
        Timestamp.from(NOW),
        Timestamp.from(NOW));
  }

  private static RequestPostProcessor operator(String subject) {
    return oidcLogin()
        .idToken(
            token -> token.issuer(ISSUER).subject(subject).claim("preferred_username", subject));
  }

  private record MapRow(String action, String reason, String subject) {}
}
