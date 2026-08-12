package dev.launchforge.application.controlplane;

import dev.launchforge.application.organization.OrganizationAccess;
import dev.launchforge.domain.controlplane.Environment;
import dev.launchforge.domain.controlplane.EnvironmentDraft;
import dev.launchforge.domain.controlplane.EnvironmentId;
import dev.launchforge.domain.controlplane.FlagDefinition;
import dev.launchforge.domain.controlplane.FlagId;
import dev.launchforge.domain.controlplane.Project;
import dev.launchforge.domain.controlplane.ProjectId;
import dev.launchforge.domain.organization.OidcIdentity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ControlPlaneRepository {
  List<Project> findProjects(OrganizationAccess access);

  Optional<ScopedProject> findProjectFor(OidcIdentity actor, ProjectId projectId);

  Optional<ScopedProject> lockProjectFor(OidcIdentity actor, ProjectId projectId);

  void insertProject(OrganizationAccess access, Project project);

  void updateProject(OrganizationAccess access, Project project, long expectedVersion);

  List<Environment> findEnvironments(OrganizationAccess access, ProjectId projectId);

  Optional<ScopedEnvironment> findEnvironmentFor(OidcIdentity actor, EnvironmentId environmentId);

  Optional<ScopedEnvironment> lockEnvironmentFor(OidcIdentity actor, EnvironmentId environmentId);

  void insertEnvironment(OrganizationAccess access, Environment environment);

  void updateEnvironment(OrganizationAccess access, Environment environment, long expectedVersion);

  List<FlagDefinition> findFlags(OrganizationAccess access, ProjectId projectId);

  Optional<ScopedFlag> findFlagFor(OidcIdentity actor, FlagId flagId);

  Optional<ScopedFlag> lockFlagFor(OidcIdentity actor, FlagId flagId);

  void insertFlag(OrganizationAccess access, FlagDefinition flag);

  void updateFlag(OrganizationAccess access, FlagDefinition flag, long expectedVersion);

  Optional<EnvironmentDraft> findDraft(
      OrganizationAccess access, FlagId flagId, EnvironmentId environmentId);

  List<EnvironmentDraft> findDrafts(OrganizationAccess access, EnvironmentId environmentId);

  void insertDraft(OrganizationAccess access, EnvironmentDraft draft);

  void updateDraft(
      OrganizationAccess access, EnvironmentDraft draft, long expectedVersion, Instant now);

  List<PublishedRevision> findRevisions(OrganizationAccess access, EnvironmentId environmentId);

  Optional<PublishedRevision> findRevision(
      OrganizationAccess access, EnvironmentId environmentId, long revision);

  List<AuditEvent> findAuditEvents(OrganizationAccess access, AuditQuery query);

  void storePublication(
      OrganizationAccess access,
      OidcIdentity actor,
      Environment environment,
      long expectedEnvironmentVersion,
      PublishedRevision revision,
      String action,
      String safeSummary,
      String outboxPayload);

  void appendAudit(
      OrganizationAccess access,
      OidcIdentity actor,
      String action,
      String targetType,
      UUID targetId,
      String safeSummary,
      String reason,
      Long fromRevision,
      Long toRevision);

  record ScopedProject(OrganizationAccess access, Project project) {}

  record ScopedEnvironment(OrganizationAccess access, Project project, Environment environment) {}

  record ScopedFlag(OrganizationAccess access, Project project, FlagDefinition flag) {}
}
