package dev.launchforge.controlapi.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.launchforge.application.controlplane.AuditRetentionConflictException;
import dev.launchforge.application.controlplane.AuditRetentionPreview;
import dev.launchforge.application.controlplane.AuditRetentionResult;
import dev.launchforge.application.controlplane.AuditRetentionService;
import dev.launchforge.application.controlplane.ControlPlaneRepository;
import dev.launchforge.application.controlplane.StoredAuditRetentionPreview;
import dev.launchforge.application.organization.OperationForbiddenException;
import dev.launchforge.application.organization.OrganizationAccess;
import dev.launchforge.application.organization.OrganizationAccessRepository;
import dev.launchforge.application.organization.UnitOfWork;
import dev.launchforge.domain.organization.MembershipId;
import dev.launchforge.domain.organization.OidcIdentity;
import dev.launchforge.domain.organization.Organization;
import dev.launchforge.domain.organization.OrganizationId;
import dev.launchforge.domain.organization.OrganizationRole;
import dev.launchforge.domain.organization.OrganizationSlug;
import dev.launchforge.domain.organization.OrganizationStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditRetentionServiceTest {
  private static final Instant NOW = Instant.parse("2026-08-17T12:00:00Z");
  private static final OidcIdentity ACTOR =
      new OidcIdentity("https://identity.example/realms/launchforge", "owner-subject");

  @Test
  void previewPersistsTheExactBoundedCandidateSet() {
    Fixture fixture = new Fixture(OrganizationRole.OWNER, false);
    List<UUID> candidates = List.of(UUID.randomUUID(), UUID.randomUUID());
    Instant cutoff = NOW.minus(Duration.ofDays(400));
    when(fixture.repository.findAuditRetentionCandidates(fixture.access, cutoff, 10))
        .thenReturn(candidates);

    AuditRetentionPreview preview =
        fixture.service.preview(ACTOR, fixture.organizationId, cutoff, 10);

    assertEquals(2, preview.candidateCount());
    ArgumentCaptor<StoredAuditRetentionPreview> captor =
        ArgumentCaptor.forClass(StoredAuditRetentionPreview.class);
    verify(fixture.repository)
        .insertAuditRetentionPreview(
            any(OrganizationAccess.class), any(OidcIdentity.class), captor.capture());
    assertEquals(candidates, captor.getValue().candidateIds());
  }

  @Test
  void viewerCannotPreviewAndRecentCutoffsAreRejected() {
    Fixture viewer = new Fixture(OrganizationRole.VIEWER, false);
    assertThrows(
        OperationForbiddenException.class,
        () ->
            viewer.service.preview(
                ACTOR, viewer.organizationId, NOW.minus(Duration.ofDays(400)), 10));
    verify(viewer.repository, never())
        .findAuditRetentionCandidates(any(), any(), any(Integer.class));

    Fixture owner = new Fixture(OrganizationRole.OWNER, false);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            owner.service.preview(ACTOR, owner.organizationId, NOW.minus(Duration.ofDays(30)), 10));
  }

  @Test
  void applyRequiresFeatureEnablementAndExactPreviewConfirmation() {
    Fixture disabled = new Fixture(OrganizationRole.OWNER, false);
    assertThrows(
        AuditRetentionConflictException.class,
        () -> disabled.service.apply(ACTOR, disabled.organizationId, UUID.randomUUID(), 1));
    verify(disabled.repository, never()).lockAuditRetentionPreview(any(), any());

    Fixture enabled = new Fixture(OrganizationRole.ADMIN, true);
    UUID previewId = UUID.randomUUID();
    StoredAuditRetentionPreview stored = enabled.storedPreview(previewId, 2);
    when(enabled.repository.lockAuditRetentionPreview(enabled.access, previewId))
        .thenReturn(Optional.of(stored));
    assertThrows(
        AuditRetentionConflictException.class,
        () -> enabled.service.apply(ACTOR, enabled.organizationId, previewId, 1));
    verify(enabled.repository, never()).applyAuditRetentionPreview(any(), any(), any(), any());

    when(enabled.repository.applyAuditRetentionPreview(enabled.access, ACTOR, stored, NOW))
        .thenReturn(2);
    AuditRetentionResult result =
        enabled.service.apply(ACTOR, enabled.organizationId, previewId, 2);
    assertEquals(2, result.deletedCount());
    assertEquals(NOW, result.preview().appliedAt());
  }

  private static final class Fixture {
    private final OrganizationId organizationId = OrganizationId.random();
    private final OrganizationAccessRepository accessRepository =
        mock(OrganizationAccessRepository.class);
    private final ControlPlaneRepository repository = mock(ControlPlaneRepository.class);
    private final OrganizationAccess access;
    private final AuditRetentionService service;

    private Fixture(OrganizationRole role, boolean deletionEnabled) {
      Organization organization =
          new Organization(
              organizationId,
              new OrganizationSlug("acme"),
              "Acme",
              OrganizationStatus.ACTIVE,
              0,
              NOW,
              NOW);
      access = new OrganizationAccess(organization, MembershipId.random(), role);
      when(accessRepository.findFor(ACTOR, organizationId)).thenReturn(Optional.of(access));
      service =
          new AuditRetentionService(
              accessRepository,
              repository,
              new DirectUnitOfWork(),
              Clock.fixed(NOW, ZoneOffset.UTC),
              Duration.ofDays(365),
              Duration.ofMinutes(15),
              1000,
              deletionEnabled);
    }

    private StoredAuditRetentionPreview storedPreview(UUID id, int count) {
      List<UUID> candidates =
          java.util.stream.Stream.generate(UUID::randomUUID).limit(count).toList();
      return new StoredAuditRetentionPreview(
          new AuditRetentionPreview(
              id,
              organizationId,
              NOW.minus(Duration.ofDays(400)),
              count,
              NOW.minusSeconds(30),
              NOW.plusSeconds(300),
              null),
          candidates);
    }
  }

  private static final class DirectUnitOfWork implements UnitOfWork {
    @Override
    public <T> T required(Supplier<T> work) {
      return work.get();
    }
  }
}
