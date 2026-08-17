package dev.launchforge.application.controlplane;

import dev.launchforge.application.controlplane.ControlPlaneRepository.ScopedEnvironment;
import dev.launchforge.application.controlplane.ControlPlaneRepository.ScopedFlag;
import dev.launchforge.application.controlplane.ControlPlaneRepository.ScopedProject;
import dev.launchforge.application.organization.OperationForbiddenException;
import dev.launchforge.application.organization.OrganizationAccess;
import dev.launchforge.application.organization.OrganizationAccessRepository;
import dev.launchforge.application.organization.OrganizationNotFoundException;
import dev.launchforge.application.organization.UnitOfWork;
import dev.launchforge.domain.controlplane.Environment;
import dev.launchforge.domain.controlplane.EnvironmentDraft;
import dev.launchforge.domain.controlplane.EnvironmentId;
import dev.launchforge.domain.controlplane.FlagDefinition;
import dev.launchforge.domain.controlplane.FlagDefinition.FlagType;
import dev.launchforge.domain.controlplane.FlagDefinition.FlagValue;
import dev.launchforge.domain.controlplane.FlagDefinition.Variation;
import dev.launchforge.domain.controlplane.FlagId;
import dev.launchforge.domain.controlplane.Project;
import dev.launchforge.domain.controlplane.ProjectId;
import dev.launchforge.domain.controlplane.ResourceKey;
import dev.launchforge.domain.controlplane.Targeting.PercentageRollout;
import dev.launchforge.domain.controlplane.Targeting.Rule;
import dev.launchforge.domain.organization.OidcIdentity;
import dev.launchforge.domain.organization.OrganizationAbility;
import dev.launchforge.domain.organization.OrganizationId;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ControlPlaneService {
  private static final int MAX_SNAPSHOT_BYTES = 5 * 1024 * 1024;
  private static final int MAX_FLAGS_PER_ENVIRONMENT = 2_000;

  private final OrganizationAccessRepository accessRepository;
  private final ControlPlaneRepository repository;
  private final UnitOfWork unitOfWork;
  private final SnapshotCodec snapshotCodec;
  private final RolloutSaltGenerator saltGenerator;
  private final Clock clock;

  public ControlPlaneService(
      OrganizationAccessRepository accessRepository,
      ControlPlaneRepository repository,
      UnitOfWork unitOfWork,
      SnapshotCodec snapshotCodec,
      RolloutSaltGenerator saltGenerator,
      Clock clock) {
    this.accessRepository = Objects.requireNonNull(accessRepository, "accessRepository");
    this.repository = Objects.requireNonNull(repository, "repository");
    this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    this.snapshotCodec = Objects.requireNonNull(snapshotCodec, "snapshotCodec");
    this.saltGenerator = Objects.requireNonNull(saltGenerator, "saltGenerator");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public Project createProject(
      OidcIdentity actor,
      OrganizationId organizationId,
      String key,
      String name,
      String description) {
    return unitOfWork.required(
        () -> {
          OrganizationAccess access = requireOrganization(actor, organizationId);
          requireAbility(access, OrganizationAbility.EDIT_DRAFT);
          Instant now = clock.instant();
          Project project =
              new Project(
                  ProjectId.random(),
                  organizationId,
                  new ResourceKey(key),
                  name,
                  description,
                  Project.Status.ACTIVE,
                  0,
                  now,
                  now);
          repository.insertProject(access, project);
          audit(
              access,
              actor,
              "PROJECT_CREATED",
              "PROJECT",
              project.id().value(),
              project.key().value(),
              null);
          return project;
        });
  }

  public List<Project> listProjects(OidcIdentity actor, OrganizationId organizationId) {
    OrganizationAccess access = requireOrganization(actor, organizationId);
    requireAbility(access, OrganizationAbility.VIEW_CONFIGURATION);
    return repository.findProjects(access);
  }

  public Project updateProject(
      OidcIdentity actor,
      ProjectId projectId,
      String name,
      String description,
      Project.Status status,
      long expectedVersion) {
    return unitOfWork.required(
        () -> {
          ScopedProject scoped = requireLockedProject(actor, projectId);
          requireAbility(scoped.access(), OrganizationAbility.EDIT_DRAFT);
          requireVersion(scoped.project().version(), expectedVersion);
          Instant now = clock.instant();
          Project updated =
              new Project(
                  scoped.project().id(),
                  scoped.project().organizationId(),
                  scoped.project().key(),
                  name,
                  description,
                  status,
                  expectedVersion + 1,
                  scoped.project().createdAt(),
                  now);
          repository.updateProject(scoped.access(), updated, expectedVersion);
          audit(
              scoped.access(),
              actor,
              "PROJECT_UPDATED",
              "PROJECT",
              projectId.value(),
              projectId.value().toString(),
              null);
          return updated;
        });
  }

  public Environment createEnvironment(
      OidcIdentity actor, ProjectId projectId, String key, String name, Environment.Kind kind) {
    return unitOfWork.required(
        () -> {
          ScopedProject scoped = requireLockedProject(actor, projectId);
          requireAbility(scoped.access(), OrganizationAbility.EDIT_DRAFT);
          requireActive(scoped.project());
          Instant now = clock.instant();
          Environment environment =
              new Environment(
                  EnvironmentId.random(),
                  projectId,
                  new ResourceKey(key),
                  name,
                  kind,
                  Environment.Status.ACTIVE,
                  0,
                  0,
                  now,
                  now);
          repository.insertEnvironment(scoped.access(), environment);
          for (FlagDefinition flag : repository.findFlags(scoped.access(), projectId)) {
            repository.insertDraft(
                scoped.access(), initialDraft(flag, environment.id(), saltGenerator.generate()));
          }
          audit(
              scoped.access(),
              actor,
              "ENVIRONMENT_CREATED",
              "ENVIRONMENT",
              environment.id().value(),
              environment.key().value(),
              null);
          return environment;
        });
  }

  public List<Environment> listEnvironments(OidcIdentity actor, ProjectId projectId) {
    ScopedProject scoped = requireProject(actor, projectId);
    requireAbility(scoped.access(), OrganizationAbility.VIEW_CONFIGURATION);
    return repository.findEnvironments(scoped.access(), projectId);
  }

  public Environment updateEnvironment(
      OidcIdentity actor,
      EnvironmentId environmentId,
      String name,
      Environment.Status status,
      long expectedVersion) {
    return unitOfWork.required(
        () -> {
          ScopedEnvironment scoped = requireLockedEnvironment(actor, environmentId);
          requireAbility(scoped.access(), OrganizationAbility.EDIT_DRAFT);
          requireVersion(scoped.environment().version(), expectedVersion);
          Environment updated =
              new Environment(
                  environmentId,
                  scoped.environment().projectId(),
                  scoped.environment().key(),
                  name,
                  scoped.environment().kind(),
                  status,
                  scoped.environment().currentRevision(),
                  expectedVersion + 1,
                  scoped.environment().createdAt(),
                  clock.instant());
          repository.updateEnvironment(scoped.access(), updated, expectedVersion);
          audit(
              scoped.access(),
              actor,
              "ENVIRONMENT_UPDATED",
              "ENVIRONMENT",
              environmentId.value(),
              name,
              null);
          return updated;
        });
  }

  public FlagDefinition createFlag(
      OidcIdentity actor,
      ProjectId projectId,
      String key,
      String name,
      FlagType type,
      boolean clientVisible,
      List<VariationInput> variationInputs) {
    return unitOfWork.required(
        () -> {
          ScopedProject scoped = requireLockedProject(actor, projectId);
          requireAbility(scoped.access(), OrganizationAbility.EDIT_DRAFT);
          requireActive(scoped.project());
          Instant now = clock.instant();
          List<Variation> variations =
              variationInputs.stream()
                  .map(
                      input ->
                          new Variation(
                              UUID.randomUUID(),
                              new ResourceKey(input.key()),
                              input.name(),
                              requireType(type, input.value())))
                  .toList();
          FlagDefinition flag =
              new FlagDefinition(
                  FlagId.random(),
                  projectId,
                  new ResourceKey(key),
                  name,
                  type,
                  clientVisible,
                  variations,
                  FlagDefinition.Status.ACTIVE,
                  0,
                  now,
                  now);
          repository.insertFlag(scoped.access(), flag);
          for (Environment environment : repository.findEnvironments(scoped.access(), projectId)) {
            repository.insertDraft(
                scoped.access(), initialDraft(flag, environment.id(), saltGenerator.generate()));
          }
          audit(
              scoped.access(),
              actor,
              "FLAG_CREATED",
              "FLAG",
              flag.id().value(),
              flag.key().value(),
              null);
          return flag;
        });
  }

  public List<FlagDefinition> listFlags(OidcIdentity actor, ProjectId projectId) {
    ScopedProject scoped = requireProject(actor, projectId);
    requireAbility(scoped.access(), OrganizationAbility.VIEW_CONFIGURATION);
    return repository.findFlags(scoped.access(), projectId);
  }

  public FlagDefinition getFlag(OidcIdentity actor, FlagId flagId) {
    ScopedFlag scoped = requireFlag(actor, flagId);
    requireAbility(scoped.access(), OrganizationAbility.VIEW_CONFIGURATION);
    return scoped.flag();
  }

  public FlagDefinition updateFlag(
      OidcIdentity actor,
      FlagId flagId,
      String name,
      FlagDefinition.Status status,
      long expectedVersion) {
    return updateFlag(actor, flagId, name, status, null, expectedVersion);
  }

  public FlagDefinition updateFlag(
      OidcIdentity actor,
      FlagId flagId,
      String name,
      FlagDefinition.Status status,
      List<VariationUpdateInput> variationInputs,
      long expectedVersion) {
    return unitOfWork.required(
        () -> {
          ScopedFlag scoped = requireLockedFlag(actor, flagId);
          requireAbility(scoped.access(), OrganizationAbility.EDIT_DRAFT);
          requireVersion(scoped.flag().version(), expectedVersion);
          List<Variation> variations =
              variationInputs == null
                  ? scoped.flag().variations()
                  : updatedVariations(scoped.flag(), variationInputs);
          FlagDefinition updated =
              new FlagDefinition(
                  flagId,
                  scoped.flag().projectId(),
                  scoped.flag().key(),
                  name,
                  scoped.flag().type(),
                  scoped.flag().clientVisible(),
                  variations,
                  status,
                  expectedVersion + 1,
                  scoped.flag().createdAt(),
                  clock.instant());
          repository.updateFlag(scoped.access(), updated, expectedVersion);
          audit(scoped.access(), actor, "FLAG_UPDATED", "FLAG", flagId.value(), name, null);
          return updated;
        });
  }

  private static List<Variation> updatedVariations(
      FlagDefinition current, List<VariationUpdateInput> inputs) {
    if (inputs.size() != current.variations().size()) {
      throw new ControlPlaneConflictException(
          "Variation identities cannot be added or removed after flag creation");
    }
    List<UUID> ids = inputs.stream().map(VariationUpdateInput::id).toList();
    if (ids.stream().distinct().count() != ids.size()) {
      throw new ControlPlaneConflictException("Variation identities must be unique");
    }
    return current.variations().stream()
        .map(
            variation -> {
              VariationUpdateInput input =
                  inputs.stream()
                      .filter(candidate -> candidate.id().equals(variation.id()))
                      .findFirst()
                      .orElseThrow(
                          () ->
                              new ControlPlaneConflictException(
                                  "Variation identities cannot change after flag creation"));
              return new Variation(
                  variation.id(),
                  variation.key(),
                  input.name(),
                  requireType(current.type(), input.value()));
            })
        .toList();
  }

  public EnvironmentDraft getDraft(OidcIdentity actor, FlagId flagId, EnvironmentId environmentId) {
    ScopedFlag scoped = requireFlag(actor, flagId);
    requireAbility(scoped.access(), OrganizationAbility.VIEW_CONFIGURATION);
    return requireDraft(scoped.access(), scoped.flag(), environmentId);
  }

  public EnvironmentDraft updateDraft(
      OidcIdentity actor,
      FlagId flagId,
      EnvironmentId environmentId,
      DraftInput input,
      long expectedVersion) {
    return unitOfWork.required(
        () -> {
          ScopedFlag scoped = requireLockedFlag(actor, flagId);
          requireAbility(scoped.access(), OrganizationAbility.EDIT_DRAFT);
          EnvironmentDraft current = requireDraft(scoped.access(), scoped.flag(), environmentId);
          requireVersion(current.version(), expectedVersion);
          EnvironmentDraft updated =
              new EnvironmentDraft(
                  flagId,
                  environmentId,
                  input.enabled(),
                  input.fallthroughVariationId(),
                  input.offVariationId(),
                  current.rolloutSalt(),
                  input.rules(),
                  input.rollout(),
                  input.changeSummary(),
                  expectedVersion + 1);
          updated.validateFor(scoped.flag());
          repository.updateDraft(scoped.access(), updated, expectedVersion, clock.instant());
          audit(
              scoped.access(),
              actor,
              "FLAG_DRAFT_UPDATED",
              "FLAG",
              flagId.value(),
              input.changeSummary(),
              null);
          return updated;
        });
  }

  public EnvironmentDraft reseedRollout(
      OidcIdentity actor,
      FlagId flagId,
      EnvironmentId environmentId,
      long expectedVersion,
      String reason) {
    requireReason(reason);
    return unitOfWork.required(
        () -> {
          ScopedFlag scoped = requireLockedFlag(actor, flagId);
          requireAbility(scoped.access(), OrganizationAbility.EDIT_DRAFT);
          EnvironmentDraft current = requireDraft(scoped.access(), scoped.flag(), environmentId);
          requireVersion(current.version(), expectedVersion);
          EnvironmentDraft updated =
              new EnvironmentDraft(
                  flagId,
                  environmentId,
                  current.enabled(),
                  current.fallthroughVariationId(),
                  current.offVariationId(),
                  saltGenerator.generate(),
                  current.rules(),
                  current.rollout(),
                  "Rollout salt deliberately reseeded: " + reason,
                  expectedVersion + 1);
          repository.updateDraft(scoped.access(), updated, expectedVersion, clock.instant());
          audit(
              scoped.access(),
              actor,
              "ROLLOUT_RESEEDED",
              "FLAG",
              flagId.value(),
              "Rollout salt deliberately reseeded",
              reason);
          return updated;
        });
  }

  public PublishedRevision publish(
      OidcIdentity actor, EnvironmentId environmentId, long expectedVersion, String reason) {
    return unitOfWork.required(
        () -> {
          ScopedEnvironment scoped = requireLockedEnvironment(actor, environmentId);
          requirePublishAbility(scoped);
          requirePublishable(scoped);
          requireVersion(scoped.environment().version(), expectedVersion);
          requireProductionReason(scoped.environment(), reason);
          return publishCurrentDraft(
              scoped, actor, expectedVersion, reason, null, "ENVIRONMENT_PUBLISHED");
        });
  }

  public PublishedRevision rollback(
      OidcIdentity actor,
      EnvironmentId environmentId,
      long sourceRevision,
      long expectedVersion,
      String reason) {
    requireReason(reason);
    return unitOfWork.required(
        () -> {
          ScopedEnvironment scoped = requireLockedEnvironment(actor, environmentId);
          requirePublishAbility(scoped);
          requirePublishable(scoped);
          requireVersion(scoped.environment().version(), expectedVersion);
          PublishedRevision source =
              repository
                  .findRevision(scoped.access(), environmentId, sourceRevision)
                  .orElseThrow(ControlPlaneNotFoundException::new);
          long nextRevision = scoped.environment().currentRevision() + 1;
          Instant now = clock.instant();
          SnapshotCodec.EncodedSnapshot encoded =
              snapshotCodec.rebase(source.canonicalSnapshot(), nextRevision, now);
          PublishedRevision revision =
              new PublishedRevision(
                  environmentId,
                  nextRevision,
                  sourceRevision,
                  encoded.checksum(),
                  encoded.canonicalJson(),
                  reason,
                  actor.subject(),
                  now);
          repository.storePublication(
              scoped.access(),
              actor,
              scoped.environment(),
              expectedVersion,
              revision,
              "ENVIRONMENT_ROLLED_BACK",
              "Restored revision " + sourceRevision + " as revision " + nextRevision,
              snapshotCodec.publicationEvent(
                  scoped.access(), scoped.environment(), nextRevision, encoded.checksum(), now));
          return revision;
        });
  }

  public DraftPreview previewDraft(OidcIdentity actor, EnvironmentId environmentId) {
    ScopedEnvironment scoped = requireEnvironment(actor, environmentId);
    requireAbility(scoped.access(), OrganizationAbility.VIEW_CONFIGURATION);
    long candidateRevision = scoped.environment().currentRevision() + 1;
    SnapshotCodec.EncodedSnapshot encoded =
        encodeCurrentDraft(scoped, candidateRevision, clock.instant());
    return new DraftPreview(
        environmentId,
        scoped.environment().currentRevision(),
        candidateRevision,
        encoded.canonicalJson());
  }

  public List<AuditEvent> auditHistory(
      OidcIdentity actor, OrganizationId organizationId, AuditQuery query) {
    OrganizationAccess access = requireOrganization(actor, organizationId);
    requireAbility(access, OrganizationAbility.VIEW_CONFIGURATION);
    AuditQuery requested = Objects.requireNonNull(query, "query");
    ScopedProject project = null;
    if (requested.projectId() != null) {
      project =
          repository
              .findProjectFor(actor, new ProjectId(requested.projectId()))
              .filter(candidate -> candidate.access().organization().id().equals(organizationId))
              .orElseThrow(ControlPlaneNotFoundException::new);
    }
    if (requested.environmentId() != null) {
      ScopedEnvironment environment =
          repository
              .findEnvironmentFor(actor, new EnvironmentId(requested.environmentId()))
              .filter(candidate -> candidate.access().organization().id().equals(organizationId))
              .orElseThrow(ControlPlaneNotFoundException::new);
      if (project != null && !environment.project().id().equals(project.project().id())) {
        throw new ControlPlaneNotFoundException();
      }
    }
    return repository.findAuditEvents(access, requested);
  }

  public List<PublishedRevision> revisionHistory(OidcIdentity actor, EnvironmentId environmentId) {
    ScopedEnvironment scoped = requireEnvironment(actor, environmentId);
    requireAbility(scoped.access(), OrganizationAbility.VIEW_CONFIGURATION);
    return repository.findRevisions(scoped.access(), environmentId);
  }

  public RevisionDiagnostics revisionDiagnostics(OidcIdentity actor, EnvironmentId environmentId) {
    ScopedEnvironment scoped = requireEnvironment(actor, environmentId);
    requireAbility(scoped.access(), OrganizationAbility.VIEW_CONFIGURATION);
    return repository.findRevisionDiagnostics(scoped.access(), environmentId);
  }

  public PublishedRevision revision(
      OidcIdentity actor, EnvironmentId environmentId, long revision) {
    ScopedEnvironment scoped = requireEnvironment(actor, environmentId);
    requireAbility(scoped.access(), OrganizationAbility.VIEW_CONFIGURATION);
    return repository
        .findRevision(scoped.access(), environmentId, revision)
        .orElseThrow(ControlPlaneNotFoundException::new);
  }

  public AnalyticsScope analyticsScope(OidcIdentity actor, EnvironmentId environmentId) {
    ScopedEnvironment scoped = requireEnvironment(actor, environmentId);
    requireAbility(scoped.access(), OrganizationAbility.VIEW_CONFIGURATION);
    return new AnalyticsScope(
        scoped.access().organization().id().value(),
        scoped.project().id().value(),
        scoped.environment().id().value(),
        scoped.project().key().value(),
        scoped.environment().key().value());
  }

  public SnapshotCodec.RevisionDiff diff(
      OidcIdentity actor, EnvironmentId environmentId, long fromRevision, long toRevision) {
    PublishedRevision from = revision(actor, environmentId, fromRevision);
    PublishedRevision to = revision(actor, environmentId, toRevision);
    return snapshotCodec.diff(from.canonicalSnapshot(), to.canonicalSnapshot());
  }

  public String canonicalizeJsonValue(String rawJson) {
    return snapshotCodec.canonicalizeJsonValue(rawJson);
  }

  public record AnalyticsScope(
      UUID organizationId,
      UUID projectId,
      UUID environmentId,
      String projectKey,
      String environmentKey) {}

  private PublishedRevision publishCurrentDraft(
      ScopedEnvironment scoped,
      OidcIdentity actor,
      long expectedVersion,
      String reason,
      Long sourceRevision,
      String action) {
    long nextRevision = scoped.environment().currentRevision() + 1;
    Instant now = clock.instant();
    SnapshotCodec.EncodedSnapshot encoded = encodeCurrentDraft(scoped, nextRevision, now);
    PublishedRevision revision =
        new PublishedRevision(
            scoped.environment().id(),
            nextRevision,
            sourceRevision,
            encoded.checksum(),
            encoded.canonicalJson(),
            reason,
            actor.subject(),
            now);
    repository.storePublication(
        scoped.access(),
        actor,
        scoped.environment(),
        expectedVersion,
        revision,
        action,
        "Published immutable environment revision " + nextRevision,
        snapshotCodec.publicationEvent(
            scoped.access(), scoped.environment(), nextRevision, encoded.checksum(), now));
    return revision;
  }

  private SnapshotCodec.EncodedSnapshot encodeCurrentDraft(
      ScopedEnvironment scoped, long revision, Instant generatedAt) {
    List<FlagDefinition> flags =
        repository.findFlags(scoped.access(), scoped.environment().projectId()).stream()
            .filter(flag -> flag.status() == FlagDefinition.Status.ACTIVE)
            .toList();
    if (flags.size() > MAX_FLAGS_PER_ENVIRONMENT) {
      throw new ControlPlaneConflictException("Environment has too many flags to publish");
    }
    List<EnvironmentDraft> drafts =
        repository.findDrafts(scoped.access(), scoped.environment().id());
    List<EnvironmentDraft> activeDrafts = new ArrayList<>();
    for (FlagDefinition flag : flags) {
      EnvironmentDraft draft =
          drafts.stream()
              .filter(candidate -> candidate.flagId().equals(flag.id()))
              .findFirst()
              .orElseThrow(() -> new ControlPlaneConflictException("Flag draft is missing"));
      draft.validateFor(flag);
      activeDrafts.add(draft);
    }
    SnapshotCodec.EncodedSnapshot encoded =
        snapshotCodec.encode(
            scoped.project(), scoped.environment(), flags, activeDrafts, revision, generatedAt);
    if (encoded.utf8Bytes() > MAX_SNAPSHOT_BYTES) {
      throw new ControlPlaneConflictException("Snapshot exceeds the 5 MiB publication limit");
    }
    return encoded;
  }

  private OrganizationAccess requireOrganization(
      OidcIdentity actor, OrganizationId organizationId) {
    return accessRepository
        .findFor(actor, organizationId)
        .orElseThrow(OrganizationNotFoundException::new);
  }

  private ScopedProject requireProject(OidcIdentity actor, ProjectId projectId) {
    return repository
        .findProjectFor(actor, projectId)
        .orElseThrow(ControlPlaneNotFoundException::new);
  }

  private ScopedProject requireLockedProject(OidcIdentity actor, ProjectId projectId) {
    return repository
        .lockProjectFor(actor, projectId)
        .orElseThrow(ControlPlaneNotFoundException::new);
  }

  private ScopedEnvironment requireEnvironment(OidcIdentity actor, EnvironmentId environmentId) {
    return repository
        .findEnvironmentFor(actor, environmentId)
        .orElseThrow(ControlPlaneNotFoundException::new);
  }

  private ScopedEnvironment requireLockedEnvironment(
      OidcIdentity actor, EnvironmentId environmentId) {
    return repository
        .lockEnvironmentFor(actor, environmentId)
        .orElseThrow(ControlPlaneNotFoundException::new);
  }

  private ScopedFlag requireFlag(OidcIdentity actor, FlagId flagId) {
    return repository.findFlagFor(actor, flagId).orElseThrow(ControlPlaneNotFoundException::new);
  }

  private ScopedFlag requireLockedFlag(OidcIdentity actor, FlagId flagId) {
    return repository.lockFlagFor(actor, flagId).orElseThrow(ControlPlaneNotFoundException::new);
  }

  private EnvironmentDraft requireDraft(
      OrganizationAccess access, FlagDefinition flag, EnvironmentId environmentId) {
    EnvironmentDraft draft =
        repository
            .findDraft(access, flag.id(), environmentId)
            .orElseThrow(ControlPlaneNotFoundException::new);
    draft.validateFor(flag);
    return draft;
  }

  private void requirePublishAbility(ScopedEnvironment scoped) {
    OrganizationAbility ability =
        scoped.environment().productionLike()
            ? OrganizationAbility.PUBLISH_PRODUCTION
            : OrganizationAbility.PUBLISH_NON_PRODUCTION;
    requireAbility(scoped.access(), ability);
  }

  private static void requireAbility(OrganizationAccess access, OrganizationAbility ability) {
    if (!access.actorRole().allows(ability)) {
      throw new OperationForbiddenException();
    }
  }

  private static void requireActive(Project project) {
    if (project.status() != Project.Status.ACTIVE) {
      throw new ControlPlaneConflictException("Archived project cannot be changed");
    }
  }

  private static void requirePublishable(ScopedEnvironment scoped) {
    requireActive(scoped.project());
    if (scoped.environment().status() != Environment.Status.ACTIVE) {
      throw new ControlPlaneConflictException("Archived environment cannot be published");
    }
  }

  private static void requireVersion(long actual, long expected) {
    if (actual != expected) {
      throw new StaleWriteException();
    }
  }

  private static FlagValue requireType(FlagType type, FlagValue value) {
    Objects.requireNonNull(value, "value");
    if (value.type() != type) {
      throw new ControlPlaneConflictException("Variation type does not match flag type");
    }
    return value;
  }

  private static EnvironmentDraft initialDraft(
      FlagDefinition flag, EnvironmentId environmentId, String salt) {
    UUID initialVariation = flag.variations().getFirst().id();
    EnvironmentDraft draft =
        new EnvironmentDraft(
            flag.id(),
            environmentId,
            false,
            initialVariation,
            initialVariation,
            salt,
            List.of(),
            null,
            "Initial safe flag configuration",
            0);
    draft.validateFor(flag);
    return draft;
  }

  private static void requireProductionReason(Environment environment, String reason) {
    if (environment.productionLike()) {
      requireReason(reason);
    }
  }

  private static void requireReason(String reason) {
    if (reason == null
        || reason.isBlank()
        || !reason.equals(reason.strip())
        || reason.length() > 500) {
      throw new ControlPlaneConflictException("A bounded human reason is required");
    }
  }

  private void audit(
      OrganizationAccess access,
      OidcIdentity actor,
      String action,
      String targetType,
      UUID targetId,
      String summary,
      String reason) {
    repository.appendAudit(
        access, actor, action, targetType, targetId, summary, reason, null, null);
  }

  public record VariationInput(String key, String name, FlagValue value) {}

  public record VariationUpdateInput(UUID id, String name, FlagValue value) {
    public VariationUpdateInput {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(value, "value");
    }
  }

  public record DraftPreview(
      EnvironmentId environmentId,
      long currentPublishedRevision,
      long candidateRevision,
      String canonicalSnapshot) {}

  public record DraftInput(
      boolean enabled,
      UUID fallthroughVariationId,
      UUID offVariationId,
      List<Rule> rules,
      PercentageRollout rollout,
      String changeSummary) {}
}
