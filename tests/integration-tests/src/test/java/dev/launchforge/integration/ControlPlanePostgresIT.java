package dev.launchforge.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.launchforge.application.controlplane.ControlPlaneConflictException;
import dev.launchforge.application.controlplane.ControlPlaneNotFoundException;
import dev.launchforge.application.controlplane.ControlPlaneRepository;
import dev.launchforge.application.controlplane.ControlPlaneService;
import dev.launchforge.application.controlplane.ControlPlaneService.DraftInput;
import dev.launchforge.application.controlplane.ControlPlaneService.VariationInput;
import dev.launchforge.application.controlplane.PublishedRevision;
import dev.launchforge.application.organization.OperationForbiddenException;
import dev.launchforge.application.organization.UnitOfWork;
import dev.launchforge.application.sdkkey.IssuedServerSdkKey;
import dev.launchforge.application.sdkkey.SdkKeyService;
import dev.launchforge.domain.controlplane.Environment;
import dev.launchforge.domain.controlplane.EnvironmentDraft;
import dev.launchforge.domain.controlplane.FlagDefinition;
import dev.launchforge.domain.controlplane.FlagDefinition.FlagType;
import dev.launchforge.domain.controlplane.FlagDefinition.FlagValue;
import dev.launchforge.domain.controlplane.Project;
import dev.launchforge.domain.controlplane.Targeting.Allocation;
import dev.launchforge.domain.controlplane.Targeting.PercentageRollout;
import dev.launchforge.domain.organization.OidcIdentity;
import dev.launchforge.domain.organization.OrganizationId;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class ControlPlanePostgresIT extends AbstractControlApiIntegrationTest {
  private static final UUID ORGANIZATION_A =
      UUID.fromString("30000000-0000-0000-0000-00000000000a");
  private static final UUID ORGANIZATION_B =
      UUID.fromString("30000000-0000-0000-0000-00000000000b");
  private static final UUID OWNER_A_MEMBERSHIP =
      UUID.fromString("40000000-0000-0000-0000-00000000000a");
  private static final UUID OWNER_B_MEMBERSHIP =
      UUID.fromString("40000000-0000-0000-0000-00000000000b");
  private static final UUID DEVELOPER_A_MEMBERSHIP =
      UUID.fromString("40000000-0000-0000-0000-00000000000c");
  private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
  private static final OidcIdentity OWNER_A = new OidcIdentity(ISSUER, "control-owner-a");
  private static final OidcIdentity OWNER_B = new OidcIdentity(ISSUER, "control-owner-b");
  private static final OidcIdentity DEVELOPER_A = new OidcIdentity(ISSUER, "control-developer-a");

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ControlPlaneService service;
  @Autowired private ControlPlaneRepository repository;
  @Autowired private UnitOfWork unitOfWork;
  @Autowired private SdkKeyService sdkKeyService;

  @BeforeEach
  void seedTenants() {
    jdbcTemplate.execute("TRUNCATE TABLE organizations CASCADE");
    insertOrganization(ORGANIZATION_A, "control-alpha", "Control Alpha");
    insertOrganization(ORGANIZATION_B, "control-beta", "Control Beta");
    insertMembership(OWNER_A_MEMBERSHIP, ORGANIZATION_A, OWNER_A.subject(), "OWNER");
    insertMembership(OWNER_B_MEMBERSHIP, ORGANIZATION_B, OWNER_B.subject(), "OWNER");
    insertMembership(DEVELOPER_A_MEMBERSHIP, ORGANIZATION_A, DEVELOPER_A.subject(), "DEVELOPER");
  }

  @Test
  void draftIsInvisibleUntilAtomicPublicationCreatesRevisionAuditAndPendingOutbox() {
    Fixture fixture = fixture("atomic", OWNER_A, ORGANIZATION_A);
    EnvironmentDraft draft = enabledDraft(fixture, 0, "Enable ten percent rollout");

    assertEquals(0, fixture.environment().currentRevision());
    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM environment_revisions WHERE environment_id = ?",
            Integer.class,
            fixture.environment().id().value()));

    PublishedRevision revision = service.publish(OWNER_A, fixture.environment().id(), 0, null);

    assertEquals(1, revision.revision());
    assertTrue(revision.canonicalSnapshot().contains("\"enabled\":true"));
    assertTrue(revision.canonicalSnapshot().contains("\"weight\":10000"));
    assertEquals(
        1L,
        jdbcTemplate.queryForObject(
            "SELECT current_revision FROM environments WHERE id = ?",
            Long.class,
            fixture.environment().id().value()));
    assertEquals(
        "PENDING",
        jdbcTemplate.queryForObject(
            "SELECT status FROM outbox_events WHERE aggregate_id = ?",
            String.class,
            fixture.environment().id().value()));
    assertEquals(
        "ENVIRONMENT_PUBLISHED",
        jdbcTemplate.queryForObject(
            "SELECT action FROM audit_events WHERE environment_id = ? AND to_revision = 1",
            String.class,
            fixture.environment().id().value()));

    EnvironmentDraft changed =
        service.updateDraft(
            OWNER_A,
            fixture.flag().id(),
            fixture.environment().id(),
            new DraftInput(
                false,
                draft.fallthroughVariationId(),
                draft.offVariationId(),
                List.of(),
                null,
                "Disable draft after publication"),
            draft.version());
    assertFalse(changed.enabled());
    assertTrue(
        service
            .revision(OWNER_A, fixture.environment().id(), 1)
            .canonicalSnapshot()
            .contains("\"enabled\":true"));
  }

  @Test
  void aLateOutboxFailureRollsBackRevisionPointerAndAudit() {
    Fixture fixture = fixture("rollback", OWNER_A, ORGANIZATION_A);

    assertThrows(
        DataAccessException.class,
        () ->
            unitOfWork.required(
                () -> {
                  ControlPlaneRepository.ScopedEnvironment scoped =
                      repository
                          .lockEnvironmentFor(OWNER_A, fixture.environment().id())
                          .orElseThrow();
                  PublishedRevision revision =
                      new PublishedRevision(
                          fixture.environment().id(),
                          1,
                          null,
                          "0".repeat(64),
                          "{}",
                          null,
                          OWNER_A.subject(),
                          Instant.now());
                  repository.storePublication(
                      scoped.access(),
                      OWNER_A,
                      scoped.environment(),
                      0,
                      revision,
                      "ENVIRONMENT_PUBLISHED",
                      "Must roll back",
                      "not-json");
                  return null;
                }));

    assertEquals(
        0L,
        jdbcTemplate.queryForObject(
            "SELECT current_revision FROM environments WHERE id = ?",
            Long.class,
            fixture.environment().id().value()));
    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM environment_revisions WHERE environment_id = ?",
            Integer.class,
            fixture.environment().id().value()));
    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM audit_events
             WHERE environment_id = ? AND action = 'ENVIRONMENT_PUBLISHED'
            """,
            Integer.class,
            fixture.environment().id().value()));
  }

  @Test
  void concurrentDraftWritesAndPublishesRejectStaleVersions() throws Exception {
    Fixture draftFixture = fixture("draft-race", OWNER_A, ORGANIZATION_A);
    CountDownLatch draftStart = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<Boolean> first =
          executor.submit(() -> updateDraftAfterSignal(draftStart, draftFixture, "first"));
      Future<Boolean> second =
          executor.submit(() -> updateDraftAfterSignal(draftStart, draftFixture, "second"));
      draftStart.countDown();
      assertTrue(first.get() ^ second.get());
    }
    assertEquals(
        1L,
        jdbcTemplate.queryForObject(
            "SELECT version FROM flag_environment_configs WHERE environment_id = ? AND flag_id = ?",
            Long.class,
            draftFixture.environment().id().value(),
            draftFixture.flag().id().value()));

    Fixture publishFixture = fixture("publish-race", OWNER_A, ORGANIZATION_A);
    CountDownLatch publishStart = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<Boolean> first =
          executor.submit(() -> publishAfterSignal(publishStart, publishFixture));
      Future<Boolean> second =
          executor.submit(() -> publishAfterSignal(publishStart, publishFixture));
      publishStart.countDown();
      assertTrue(first.get() ^ second.get());
    }
    assertEquals(
        1L,
        jdbcTemplate.queryForObject(
            "SELECT current_revision FROM environments WHERE id = ?",
            Long.class,
            publishFixture.environment().id().value()));
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM environment_revisions WHERE environment_id = ?",
            Integer.class,
            publishFixture.environment().id().value()));
  }

  @Test
  void directResourceIdsCannotCrossTenantBoundary() {
    Fixture beta = fixture("tenant-beta", OWNER_B, ORGANIZATION_B);

    assertThrows(
        ControlPlaneNotFoundException.class,
        () -> service.listEnvironments(OWNER_A, beta.project().id()));
    assertThrows(
        ControlPlaneNotFoundException.class, () -> service.getFlag(OWNER_A, beta.flag().id()));
    assertThrows(
        ControlPlaneNotFoundException.class,
        () -> service.publish(OWNER_A, beta.environment().id(), 0, null));

    assertEquals(
        0L,
        jdbcTemplate.queryForObject(
            "SELECT current_revision FROM environments WHERE id = ?",
            Long.class,
            beta.environment().id().value()));
  }

  @Test
  void revisionRowsAreDatabaseImmutableAndRollbackCreatesHigherRevision() {
    Fixture fixture = fixture("history", OWNER_A, ORGANIZATION_A);
    PublishedRevision first = service.publish(OWNER_A, fixture.environment().id(), 0, null);
    EnvironmentDraft changed = enabledDraft(fixture, 0, "Enable before second revision");
    PublishedRevision second = service.publish(OWNER_A, fixture.environment().id(), 1, null);
    String originalFirstSnapshot = first.canonicalSnapshot();

    PublishedRevision rollback =
        service.rollback(
            OWNER_A,
            fixture.environment().id(),
            first.revision(),
            2,
            "Restore the known-safe disabled configuration");

    assertEquals(2, second.revision());
    assertEquals(3, rollback.revision());
    assertEquals(1L, rollback.sourceRevision());
    assertNotEquals(first.checksum(), rollback.checksum());
    assertEquals(
        3L,
        jdbcTemplate.queryForObject(
            "SELECT current_revision FROM environments WHERE id = ?",
            Long.class,
            fixture.environment().id().value()));
    assertEquals(
        originalFirstSnapshot,
        service.revision(OWNER_A, fixture.environment().id(), 1).canonicalSnapshot());
    assertTrue(service.diff(OWNER_A, fixture.environment().id(), 1, 3).changedFlagKeys().isEmpty());
    assertEquals(3, service.revisionHistory(OWNER_A, fixture.environment().id()).size());
    assertEquals(1, changed.version());

    assertThrows(
        DataAccessException.class,
        () ->
            jdbcTemplate.update(
                "UPDATE environment_revisions SET checksum_sha256 = ? WHERE environment_id = ? AND revision = 1",
                "f".repeat(64),
                fixture.environment().id().value()));
    assertThrows(
        DataAccessException.class,
        () ->
            jdbcTemplate.update(
                "DELETE FROM environment_revisions WHERE environment_id = ? AND revision = 1",
                fixture.environment().id().value()));
  }

  @Test
  void browserApiRequiresCsrfAndIfMatchAndRejectsTypedVariationMismatch() throws Exception {
    Fixture fixture = fixture("api", OWNER_A, ORGANIZATION_A);
    String updateBody = "{\"name\":\"Renamed checkout\",\"status\":\"ACTIVE\"}";

    mockMvc
        .perform(
            patch("/api/v1/flags/{flagId}", fixture.flag().id().value())
                .with(operator(OWNER_A.subject()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            patch("/api/v1/flags/{flagId}", fixture.flag().id().value())
                .with(operator(OWNER_A.subject()))
                .with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isPreconditionRequired());
    String etag =
        mockMvc
            .perform(
                patch("/api/v1/flags/{flagId}", fixture.flag().id().value())
                    .with(operator(OWNER_A.subject()))
                    .with(csrf().asHeader())
                    .header(HttpHeaders.IF_MATCH, "\"0\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateBody))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader(HttpHeaders.ETAG);
    assertEquals("\"1\"", etag);
    mockMvc
        .perform(
            patch("/api/v1/flags/{flagId}", fixture.flag().id().value())
                .with(operator(OWNER_A.subject()))
                .with(csrf().asHeader())
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isConflict());

    mockMvc
        .perform(
            post("/api/v1/projects/{projectId}/flags", fixture.project().id().value())
                .with(operator(OWNER_A.subject()))
                .with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"key":"bad.number","name":"Bad number","type":"NUMBER",
                     "clientVisible":false,
                     "variations":[
                       {"key":"a","name":"A","value":"not-a-number"},
                       {"key":"b","name":"B","value":2}
                     ]}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void productionPublicationRequiresPrivilegedRoleAndHumanReason() {
    Project project =
        service.createProject(
            OWNER_A,
            new OrganizationId(ORGANIZATION_A),
            "production-project",
            "Production Project",
            null);
    Environment environment =
        service.createEnvironment(
            OWNER_A, project.id(), "production", "Production", Environment.Kind.PRODUCTION);
    service.createFlag(
        OWNER_A,
        project.id(),
        "release.enabled",
        "Release enabled",
        FlagType.BOOLEAN,
        false,
        List.of(
            new VariationInput("off", "Off", FlagValue.bool(false)),
            new VariationInput("on", "On", FlagValue.bool(true))));

    assertThrows(
        OperationForbiddenException.class,
        () -> service.publish(DEVELOPER_A, environment.id(), 0, "Developer attempt"));
    assertThrows(
        ControlPlaneConflictException.class,
        () -> service.publish(OWNER_A, environment.id(), 0, null));
    assertEquals(
        1,
        service.publish(OWNER_A, environment.id(), 0, "Approve the production release").revision());
  }

  @Test
  void serverSdkKeyLifecycleReturnsSecretOnceAndRemainsTenantScoped() throws Exception {
    Fixture alpha = fixture("sdk-alpha", OWNER_A, ORGANIZATION_A);
    Fixture beta = fixture("sdk-beta", OWNER_B, ORGANIZATION_B);

    IssuedServerSdkKey issued =
        sdkKeyService.create(OWNER_A, alpha.environment().id(), "Storefront server", null);
    assertTrue(issued.credential().matches("lf_srv_[A-Za-z0-9_-]{16}_[A-Za-z0-9_-]{43}"));
    assertEquals(
        32,
        jdbcTemplate.queryForObject(
            "SELECT OCTET_LENGTH(secret_verifier) FROM sdk_keys WHERE id = ?",
            Integer.class,
            issued.metadata().id().value()));
    assertFalse(
        jdbcTemplate
            .queryForList("SELECT * FROM sdk_keys WHERE id = ?", issued.metadata().id().value())
            .getFirst()
            .containsValue(issued.credential()));
    assertThrows(
        ControlPlaneNotFoundException.class,
        () -> sdkKeyService.list(OWNER_A, beta.environment().id()));

    IssuedServerSdkKey replacement =
        sdkKeyService.rotate(OWNER_A, issued.metadata().id(), Duration.ofMinutes(5), null);
    assertNotEquals(issued.credential(), replacement.credential());
    assertEquals(
        "ACTIVE",
        jdbcTemplate.queryForObject(
            "SELECT status FROM sdk_keys WHERE id = ?",
            String.class,
            issued.metadata().id().value()));
    assertTrue(
        jdbcTemplate
            .queryForObject(
                "SELECT expires_at FROM sdk_keys WHERE id = ?",
                Timestamp.class,
                issued.metadata().id().value())
            .toInstant()
            .isAfter(NOW));
    sdkKeyService.revoke(OWNER_A, replacement.metadata().id());
    assertEquals(
        "REVOKED",
        jdbcTemplate.queryForObject(
            "SELECT status FROM sdk_keys WHERE id = ?",
            String.class,
            replacement.metadata().id().value()));

    String createdBody =
        mockMvc
            .perform(
                post(
                        "/api/v1/environments/{environmentId}/sdk-keys",
                        alpha.environment().id().value())
                    .with(operator(OWNER_A.subject()))
                    .with(csrf().asHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"API server\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(createdBody.contains("\"secret\":\"lf_srv_"));
    String listBody =
        mockMvc
            .perform(
                get(
                        "/api/v1/environments/{environmentId}/sdk-keys",
                        alpha.environment().id().value())
                    .with(operator(OWNER_A.subject())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertFalse(listBody.contains("secret"));
  }

  private Fixture fixture(String suffix, OidcIdentity actor, UUID organizationId) {
    Project project =
        service.createProject(
            actor,
            new OrganizationId(organizationId),
            "project." + suffix,
            "Project " + suffix,
            "Integration fixture");
    Environment environment =
        service.createEnvironment(
            actor,
            project.id(),
            "development." + suffix,
            "Development " + suffix,
            Environment.Kind.DEVELOPMENT);
    FlagDefinition flag =
        service.createFlag(
            actor,
            project.id(),
            "checkout." + suffix,
            "Checkout " + suffix,
            FlagType.BOOLEAN,
            true,
            List.of(
                new VariationInput("off", "Off", FlagValue.bool(false)),
                new VariationInput("on", "On", FlagValue.bool(true))));
    return new Fixture(project, environment, flag);
  }

  private EnvironmentDraft enabledDraft(Fixture fixture, long expectedVersion, String summary) {
    UUID off = fixture.flag().variations().getFirst().id();
    UUID on = fixture.flag().variations().getLast().id();
    return service.updateDraft(
        OWNER_A,
        fixture.flag().id(),
        fixture.environment().id(),
        new DraftInput(
            true,
            on,
            off,
            List.of(),
            new PercentageRollout(
                "userId", List.of(new Allocation(on, 10_000), new Allocation(off, 90_000))),
            summary),
        expectedVersion);
  }

  private boolean updateDraftAfterSignal(CountDownLatch start, Fixture fixture, String summary)
      throws InterruptedException {
    start.await();
    try {
      UUID off = fixture.flag().variations().getFirst().id();
      UUID on = fixture.flag().variations().getLast().id();
      service.updateDraft(
          OWNER_A,
          fixture.flag().id(),
          fixture.environment().id(),
          new DraftInput(true, on, off, List.of(), null, summary),
          0);
      return true;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private boolean publishAfterSignal(CountDownLatch start, Fixture fixture)
      throws InterruptedException {
    start.await();
    try {
      service.publish(OWNER_A, fixture.environment().id(), 0, null);
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

  private record Fixture(Project project, Environment environment, FlagDefinition flag) {}
}
