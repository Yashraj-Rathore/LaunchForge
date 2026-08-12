package dev.launchforge.infrastructure.controlplane;

import dev.launchforge.application.controlplane.AuditEvent;
import dev.launchforge.application.controlplane.AuditQuery;
import dev.launchforge.application.controlplane.ControlPlaneConflictException;
import dev.launchforge.application.controlplane.ControlPlaneNotFoundException;
import dev.launchforge.application.controlplane.ControlPlaneRepository;
import dev.launchforge.application.controlplane.PublishedRevision;
import dev.launchforge.application.controlplane.StaleWriteException;
import dev.launchforge.application.organization.OrganizationAccess;
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
import dev.launchforge.domain.organization.MembershipId;
import dev.launchforge.domain.organization.OidcIdentity;
import dev.launchforge.domain.organization.Organization;
import dev.launchforge.domain.organization.OrganizationId;
import dev.launchforge.domain.organization.OrganizationRole;
import dev.launchforge.domain.organization.OrganizationSlug;
import dev.launchforge.domain.organization.OrganizationStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcControlPlanePersistence implements ControlPlaneRepository {
  private static final TypeReference<List<Rule>> RULE_LIST = new TypeReference<>() {};
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public JdbcControlPlanePersistence(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<Project> findProjects(OrganizationAccess access) {
    return jdbcTemplate.query(
        """
        SELECT p.*
          FROM projects p
         WHERE p.organization_id = ?
           AND EXISTS (
             SELECT 1 FROM organization_memberships actor
              WHERE actor.id = ? AND actor.organization_id = p.organization_id)
         ORDER BY p.project_key, p.id
        """,
        projectRowMapper(),
        access.organization().id().value(),
        access.actorMembershipId().value());
  }

  @Override
  public Optional<ScopedProject> findProjectFor(OidcIdentity actor, ProjectId projectId) {
    return scopedProject(actor, projectId, false);
  }

  @Override
  public Optional<ScopedProject> lockProjectFor(OidcIdentity actor, ProjectId projectId) {
    return scopedProject(actor, projectId, true);
  }

  @Override
  public void insertProject(OrganizationAccess access, Project project) {
    try {
      int inserted =
          jdbcTemplate.update(
              """
              INSERT INTO projects
                (id, organization_id, project_key, name, description, status, version,
                 created_at, updated_at)
              SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?
               WHERE EXISTS (
                 SELECT 1 FROM organization_memberships actor
                  WHERE actor.id = ? AND actor.organization_id = ?)
              """,
              project.id().value(),
              project.organizationId().value(),
              project.key().value(),
              project.name(),
              project.description(),
              project.status().name(),
              project.version(),
              Timestamp.from(project.createdAt()),
              Timestamp.from(project.updatedAt()),
              access.actorMembershipId().value(),
              access.organization().id().value());
      requireScopedMutation(inserted);
    } catch (DuplicateKeyException exception) {
      throw new ControlPlaneConflictException("Project key already exists");
    }
  }

  @Override
  public void updateProject(OrganizationAccess access, Project project, long expectedVersion) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE projects p
               SET name = ?, description = ?, status = ?, version = ?, updated_at = ?
             WHERE p.id = ? AND p.organization_id = ? AND p.version = ?
               AND EXISTS (
                 SELECT 1 FROM organization_memberships actor
                  WHERE actor.id = ? AND actor.organization_id = p.organization_id)
            """,
            project.name(),
            project.description(),
            project.status().name(),
            project.version(),
            Timestamp.from(project.updatedAt()),
            project.id().value(),
            project.organizationId().value(),
            expectedVersion,
            access.actorMembershipId().value());
    requireVersionedMutation(updated);
  }

  @Override
  public List<Environment> findEnvironments(OrganizationAccess access, ProjectId projectId) {
    return jdbcTemplate.query(
        """
        SELECT e.*
          FROM environments e
         WHERE e.organization_id = ? AND e.project_id = ?
           AND EXISTS (
             SELECT 1 FROM organization_memberships actor
              WHERE actor.id = ? AND actor.organization_id = e.organization_id)
         ORDER BY e.environment_key, e.id
        """,
        environmentRowMapper(),
        access.organization().id().value(),
        projectId.value(),
        access.actorMembershipId().value());
  }

  @Override
  public Optional<ScopedEnvironment> findEnvironmentFor(
      OidcIdentity actor, EnvironmentId environmentId) {
    return scopedEnvironment(actor, environmentId, false);
  }

  @Override
  public Optional<ScopedEnvironment> lockEnvironmentFor(
      OidcIdentity actor, EnvironmentId environmentId) {
    return scopedEnvironment(actor, environmentId, true);
  }

  @Override
  public void insertEnvironment(OrganizationAccess access, Environment environment) {
    try {
      int inserted =
          jdbcTemplate.update(
              """
              INSERT INTO environments
                (id, organization_id, project_id, environment_key, name, kind, status,
                 current_revision, version, created_at, updated_at)
              SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
               WHERE EXISTS (
                 SELECT 1 FROM projects p
                  WHERE p.id = ? AND p.organization_id = ?)
                 AND EXISTS (
                   SELECT 1 FROM organization_memberships actor
                    WHERE actor.id = ? AND actor.organization_id = ?)
              """,
              environment.id().value(),
              access.organization().id().value(),
              environment.projectId().value(),
              environment.key().value(),
              environment.name(),
              environment.kind().name(),
              environment.status().name(),
              environment.currentRevision(),
              environment.version(),
              Timestamp.from(environment.createdAt()),
              Timestamp.from(environment.updatedAt()),
              environment.projectId().value(),
              access.organization().id().value(),
              access.actorMembershipId().value(),
              access.organization().id().value());
      requireScopedMutation(inserted);
    } catch (DuplicateKeyException exception) {
      throw new ControlPlaneConflictException("Environment key already exists");
    }
  }

  @Override
  public void updateEnvironment(
      OrganizationAccess access, Environment environment, long expectedVersion) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE environments e
               SET name = ?, status = ?, current_revision = ?, version = ?, updated_at = ?
             WHERE e.id = ? AND e.organization_id = ? AND e.version = ?
               AND EXISTS (
                 SELECT 1 FROM organization_memberships actor
                  WHERE actor.id = ? AND actor.organization_id = e.organization_id)
            """,
            environment.name(),
            environment.status().name(),
            environment.currentRevision(),
            environment.version(),
            Timestamp.from(environment.updatedAt()),
            environment.id().value(),
            access.organization().id().value(),
            expectedVersion,
            access.actorMembershipId().value());
    requireVersionedMutation(updated);
  }

  @Override
  public List<FlagDefinition> findFlags(OrganizationAccess access, ProjectId projectId) {
    return jdbcTemplate.query(
        """
        SELECT f.*, v.id AS variation_id, v.variation_key, v.name AS variation_name,
               v.canonical_value, v.position
          FROM flags f
          JOIN flag_variations v
            ON v.organization_id = f.organization_id
           AND v.project_id = f.project_id
           AND v.flag_id = f.id
         WHERE f.organization_id = ? AND f.project_id = ?
           AND EXISTS (
             SELECT 1 FROM organization_memberships actor
              WHERE actor.id = ? AND actor.organization_id = f.organization_id)
         ORDER BY f.flag_key, f.id, v.position
        """,
        (ResultSetExtractor<List<FlagDefinition>>) this::extractFlags,
        access.organization().id().value(),
        projectId.value(),
        access.actorMembershipId().value());
  }

  @Override
  public Optional<ScopedFlag> findFlagFor(OidcIdentity actor, FlagId flagId) {
    return scopedFlag(actor, flagId, false);
  }

  @Override
  public Optional<ScopedFlag> lockFlagFor(OidcIdentity actor, FlagId flagId) {
    return scopedFlag(actor, flagId, true);
  }

  @Override
  public void insertFlag(OrganizationAccess access, FlagDefinition flag) {
    try {
      int inserted =
          jdbcTemplate.update(
              """
              INSERT INTO flags
                (id, organization_id, project_id, flag_key, name, flag_type, client_visible,
                 status, version, created_at, updated_at)
              SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
               WHERE EXISTS (
                 SELECT 1 FROM projects p
                  WHERE p.id = ? AND p.organization_id = ?)
                 AND EXISTS (
                   SELECT 1 FROM organization_memberships actor
                    WHERE actor.id = ? AND actor.organization_id = ?)
              """,
              flag.id().value(),
              access.organization().id().value(),
              flag.projectId().value(),
              flag.key().value(),
              flag.name(),
              flag.type().name(),
              flag.clientVisible(),
              flag.status().name(),
              flag.version(),
              Timestamp.from(flag.createdAt()),
              Timestamp.from(flag.updatedAt()),
              flag.projectId().value(),
              access.organization().id().value(),
              access.actorMembershipId().value(),
              access.organization().id().value());
      requireScopedMutation(inserted);
      for (int position = 0; position < flag.variations().size(); position++) {
        Variation variation = flag.variations().get(position);
        jdbcTemplate.update(
            """
            INSERT INTO flag_variations
              (id, organization_id, project_id, flag_id, variation_key, name,
               canonical_value, position)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            variation.id(),
            access.organization().id().value(),
            flag.projectId().value(),
            flag.id().value(),
            variation.key().value(),
            variation.name(),
            variation.value().canonicalValue(),
            position);
      }
    } catch (DuplicateKeyException exception) {
      throw new ControlPlaneConflictException("Flag or variation key already exists");
    }
  }

  @Override
  public void updateFlag(OrganizationAccess access, FlagDefinition flag, long expectedVersion) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE flags f
               SET name = ?, status = ?, version = ?, updated_at = ?
             WHERE f.id = ? AND f.organization_id = ? AND f.version = ?
               AND EXISTS (
                 SELECT 1 FROM organization_memberships actor
                  WHERE actor.id = ? AND actor.organization_id = f.organization_id)
            """,
            flag.name(),
            flag.status().name(),
            flag.version(),
            Timestamp.from(flag.updatedAt()),
            flag.id().value(),
            access.organization().id().value(),
            expectedVersion,
            access.actorMembershipId().value());
    requireVersionedMutation(updated);
    for (Variation variation : flag.variations()) {
      int variationUpdated =
          jdbcTemplate.update(
              """
              UPDATE flag_variations v
                 SET name = ?, canonical_value = ?
               WHERE v.id = ? AND v.flag_id = ? AND v.organization_id = ?
                 AND EXISTS (
                   SELECT 1 FROM organization_memberships actor
                    WHERE actor.id = ? AND actor.organization_id = v.organization_id)
              """,
              variation.name(),
              variation.value().canonicalValue(),
              variation.id(),
              flag.id().value(),
              access.organization().id().value(),
              access.actorMembershipId().value());
      if (variationUpdated != 1) {
        throw new ControlPlaneConflictException("Flag variation could not be updated safely");
      }
    }
  }

  @Override
  public Optional<EnvironmentDraft> findDraft(
      OrganizationAccess access, FlagId flagId, EnvironmentId environmentId) {
    List<EnvironmentDraft> matches =
        jdbcTemplate.query(
            """
            SELECT d.*
              FROM flag_environment_configs d
             WHERE d.organization_id = ? AND d.flag_id = ? AND d.environment_id = ?
               AND EXISTS (
                 SELECT 1 FROM organization_memberships actor
                  WHERE actor.id = ? AND actor.organization_id = d.organization_id)
               AND EXISTS (
                 SELECT 1 FROM flags f JOIN environments e ON e.project_id = f.project_id
                  WHERE f.id = d.flag_id AND e.id = d.environment_id
                    AND f.organization_id = d.organization_id
                    AND e.organization_id = d.organization_id)
            """,
            draftRowMapper(),
            access.organization().id().value(),
            flagId.value(),
            environmentId.value(),
            access.actorMembershipId().value());
    return matches.stream().findFirst();
  }

  @Override
  public List<EnvironmentDraft> findDrafts(OrganizationAccess access, EnvironmentId environmentId) {
    return jdbcTemplate.query(
        """
        SELECT d.*
          FROM flag_environment_configs d
         WHERE d.organization_id = ? AND d.environment_id = ?
           AND EXISTS (
             SELECT 1 FROM organization_memberships actor
              WHERE actor.id = ? AND actor.organization_id = d.organization_id)
         ORDER BY d.flag_id
        """,
        draftRowMapper(),
        access.organization().id().value(),
        environmentId.value(),
        access.actorMembershipId().value());
  }

  @Override
  public void insertDraft(OrganizationAccess access, EnvironmentDraft draft) {
    int inserted =
        jdbcTemplate.update(
            """
            INSERT INTO flag_environment_configs
              (organization_id, project_id, environment_id, flag_id, enabled,
               fallthrough_variation_id, off_variation_id, rollout_salt, rules, rollout,
               change_summary, version, updated_at)
            SELECT f.organization_id, f.project_id, ?, f.id, ?, ?, ?, ?, CAST(? AS jsonb),
                   CAST(? AS jsonb), ?, ?, CURRENT_TIMESTAMP
              FROM flags f
              JOIN environments e
                ON e.organization_id = f.organization_id AND e.project_id = f.project_id
             WHERE f.id = ? AND e.id = ? AND f.organization_id = ?
               AND EXISTS (
                 SELECT 1 FROM organization_memberships actor
                  WHERE actor.id = ? AND actor.organization_id = f.organization_id)
            """,
            draft.environmentId().value(),
            draft.enabled(),
            draft.fallthroughVariationId(),
            draft.offVariationId(),
            draft.rolloutSalt(),
            writeJson(draft.rules()),
            writeNullableJson(draft.rollout()),
            draft.changeSummary(),
            draft.version(),
            draft.flagId().value(),
            draft.environmentId().value(),
            access.organization().id().value(),
            access.actorMembershipId().value());
    requireScopedMutation(inserted);
  }

  @Override
  public void updateDraft(
      OrganizationAccess access, EnvironmentDraft draft, long expectedVersion, Instant now) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE flag_environment_configs d
               SET enabled = ?, fallthrough_variation_id = ?, off_variation_id = ?,
                   rollout_salt = ?, rules = CAST(? AS jsonb), rollout = CAST(? AS jsonb),
                   change_summary = ?, version = ?, updated_at = ?
             WHERE d.organization_id = ? AND d.flag_id = ? AND d.environment_id = ?
               AND d.version = ?
               AND EXISTS (
                 SELECT 1 FROM organization_memberships actor
                  WHERE actor.id = ? AND actor.organization_id = d.organization_id)
            """,
            draft.enabled(),
            draft.fallthroughVariationId(),
            draft.offVariationId(),
            draft.rolloutSalt(),
            writeJson(draft.rules()),
            writeNullableJson(draft.rollout()),
            draft.changeSummary(),
            draft.version(),
            Timestamp.from(now),
            access.organization().id().value(),
            draft.flagId().value(),
            draft.environmentId().value(),
            expectedVersion,
            access.actorMembershipId().value());
    requireVersionedMutation(updated);
  }

  @Override
  public List<PublishedRevision> findRevisions(
      OrganizationAccess access, EnvironmentId environmentId) {
    return jdbcTemplate.query(
        """
        SELECT r.*
          FROM environment_revisions r
         WHERE r.organization_id = ? AND r.environment_id = ?
           AND EXISTS (
             SELECT 1 FROM organization_memberships actor
              WHERE actor.id = ? AND actor.organization_id = r.organization_id)
         ORDER BY r.revision DESC
        """,
        revisionRowMapper(),
        access.organization().id().value(),
        environmentId.value(),
        access.actorMembershipId().value());
  }

  @Override
  public Optional<PublishedRevision> findRevision(
      OrganizationAccess access, EnvironmentId environmentId, long revision) {
    List<PublishedRevision> matches =
        jdbcTemplate.query(
            """
            SELECT r.*
              FROM environment_revisions r
             WHERE r.organization_id = ? AND r.environment_id = ? AND r.revision = ?
               AND EXISTS (
                 SELECT 1 FROM organization_memberships actor
                  WHERE actor.id = ? AND actor.organization_id = r.organization_id)
            """,
            revisionRowMapper(),
            access.organization().id().value(),
            environmentId.value(),
            revision,
            access.actorMembershipId().value());
    return matches.stream().findFirst();
  }

  @Override
  public List<AuditEvent> findAuditEvents(OrganizationAccess access, AuditQuery query) {
    return jdbcTemplate.query(
        """
        SELECT a.*
          FROM audit_events a
         WHERE a.organization_id = ?
           AND EXISTS (
             SELECT 1 FROM organization_memberships actor
              WHERE actor.id = ? AND actor.organization_id = a.organization_id)
           AND (CAST(? AS uuid) IS NULL OR a.project_id = CAST(? AS uuid))
           AND (CAST(? AS uuid) IS NULL OR a.environment_id = CAST(? AS uuid))
           AND (CAST(? AS varchar) IS NULL OR a.actor_subject = CAST(? AS varchar))
           AND (CAST(? AS varchar) IS NULL OR a.action = CAST(? AS varchar))
           AND (CAST(? AS timestamptz) IS NULL OR a.created_at >= CAST(? AS timestamptz))
           AND (CAST(? AS timestamptz) IS NULL OR a.created_at <= CAST(? AS timestamptz))
         ORDER BY a.created_at DESC, a.id
         LIMIT ?
        """,
        (resultSet, rowNumber) -> auditEvent(resultSet),
        access.organization().id().value(),
        access.actorMembershipId().value(),
        query.projectId(),
        query.projectId(),
        query.environmentId(),
        query.environmentId(),
        query.actorSubject(),
        query.actorSubject(),
        query.action(),
        query.action(),
        timestamp(query.from()),
        timestamp(query.from()),
        timestamp(query.to()),
        timestamp(query.to()),
        query.limit());
  }

  @Override
  public void storePublication(
      OrganizationAccess access,
      OidcIdentity actor,
      Environment environment,
      long expectedEnvironmentVersion,
      PublishedRevision revision,
      String action,
      String safeSummary,
      String outboxPayload) {
    int inserted =
        jdbcTemplate.update(
            """
            INSERT INTO environment_revisions
              (organization_id, project_id, environment_id, revision, source_revision,
               schema_version, snapshot_json, canonical_snapshot, checksum_sha256,
               human_reason, actor_issuer, actor_subject, created_at)
            SELECT e.organization_id, e.project_id, e.id, ?, ?, 1, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?
              FROM environments e
             WHERE e.id = ? AND e.organization_id = ? AND e.version = ?
               AND EXISTS (
                 SELECT 1 FROM organization_memberships member
                  WHERE member.id = ? AND member.organization_id = e.organization_id)
            """,
            revision.revision(),
            revision.sourceRevision(),
            revision.canonicalSnapshot(),
            revision.canonicalSnapshot(),
            revision.checksum(),
            revision.reason(),
            actor.issuer(),
            actor.subject(),
            Timestamp.from(revision.createdAt()),
            environment.id().value(),
            access.organization().id().value(),
            expectedEnvironmentVersion,
            access.actorMembershipId().value());
    requireVersionedMutation(inserted);

    int advanced =
        jdbcTemplate.update(
            """
            UPDATE environments e
               SET current_revision = ?, version = version + 1, updated_at = ?
             WHERE e.id = ? AND e.organization_id = ? AND e.version = ?
               AND EXISTS (
                 SELECT 1 FROM organization_memberships member
                  WHERE member.id = ? AND member.organization_id = e.organization_id)
            """,
            revision.revision(),
            Timestamp.from(revision.createdAt()),
            environment.id().value(),
            access.organization().id().value(),
            expectedEnvironmentVersion,
            access.actorMembershipId().value());
    requireVersionedMutation(advanced);

    appendAudit(
        access,
        actor,
        action,
        "ENVIRONMENT",
        environment.id().value(),
        safeSummary,
        revision.reason(),
        environment.currentRevision() == 0 ? null : environment.currentRevision(),
        revision.revision());

    jdbcTemplate.update(
        """
        INSERT INTO outbox_events
          (id, organization_id, aggregate_type, aggregate_id, aggregate_revision,
           event_type, schema_version, payload, status, attempt_count, available_at, created_at)
        VALUES (?, ?, 'ENVIRONMENT', ?, ?, 'config.revision-published.v1', 1,
                CAST(? AS jsonb), 'PENDING', 0, ?, ?)
        """,
        UUID.randomUUID(),
        access.organization().id().value(),
        environment.id().value(),
        revision.revision(),
        outboxPayload,
        Timestamp.from(revision.createdAt()),
        Timestamp.from(revision.createdAt()));
  }

  @Override
  public void appendAudit(
      OrganizationAccess access,
      OidcIdentity actor,
      String action,
      String targetType,
      UUID targetId,
      String safeSummary,
      String reason,
      Long fromRevision,
      Long toRevision) {
    int inserted =
        jdbcTemplate.update(
            """
            INSERT INTO audit_events
              (id, organization_id, project_id, environment_id, actor_issuer, actor_subject,
               action, target_type, target_id, reason_code, safe_summary, human_reason,
               from_revision, to_revision, correlation_id, created_at)
            SELECT ?, ?,
                   CASE
                     WHEN ? = 'PROJECT' THEN ?
                     WHEN ? = 'ENVIRONMENT' THEN
                       (SELECT e.project_id FROM environments e
                         WHERE e.id = ? AND e.organization_id = ?)
                     WHEN ? = 'FLAG' THEN
                       (SELECT f.project_id FROM flags f
                         WHERE f.id = ? AND f.organization_id = ?)
                   END,
                   CASE WHEN ? = 'ENVIRONMENT' THEN ? END,
                   ?, ?, ?, ?, ?, 'SUCCESS', ?, ?, ?, ?, ?, CURRENT_TIMESTAMP
             WHERE EXISTS (
               SELECT 1 FROM organization_memberships member
                WHERE member.id = ? AND member.organization_id = ?)
            """,
            UUID.randomUUID(),
            access.organization().id().value(),
            targetType,
            targetId,
            targetType,
            targetId,
            access.organization().id().value(),
            targetType,
            targetId,
            access.organization().id().value(),
            targetType,
            targetId,
            actor.issuer(),
            actor.subject(),
            action,
            targetType,
            targetId,
            safeSummary,
            reason,
            fromRevision,
            toRevision,
            UUID.randomUUID(),
            access.actorMembershipId().value(),
            access.organization().id().value());
    requireScopedMutation(inserted);
  }

  private Optional<ScopedProject> scopedProject(
      OidcIdentity actor, ProjectId projectId, boolean lock) {
    String locking = lock ? " FOR UPDATE OF p" : "";
    List<ScopedProject> matches =
        jdbcTemplate.query(
            """
            SELECT p.*, o.slug AS organization_slug, o.name AS organization_name,
                   o.status AS organization_status, o.version AS organization_version,
                   o.created_at AS organization_created_at, o.updated_at AS organization_updated_at,
                   m.id AS actor_membership_id, m.role AS actor_role
              FROM projects p
              JOIN organizations o ON o.id = p.organization_id
              JOIN organization_memberships m ON m.organization_id = o.id
             WHERE p.id = ? AND m.oidc_issuer = ? AND m.oidc_subject = ?
            """
                + locking,
            (resultSet, rowNumber) ->
                new ScopedProject(mapAccess(resultSet), mapProject(resultSet)),
            projectId.value(),
            actor.issuer(),
            actor.subject());
    return matches.stream().findFirst();
  }

  private Optional<ScopedEnvironment> scopedEnvironment(
      OidcIdentity actor, EnvironmentId environmentId, boolean lock) {
    String locking = lock ? " FOR UPDATE OF p, e" : "";
    List<ScopedEnvironment> matches =
        jdbcTemplate.query(
            """
            SELECT e.*, p.id AS project_id_value, p.organization_id, p.project_key,
                   p.name AS project_name,
                   p.description, p.status AS project_status, p.version AS project_version,
                   p.created_at AS project_created_at, p.updated_at AS project_updated_at,
                   o.slug AS organization_slug, o.name AS organization_name,
                   o.status AS organization_status, o.version AS organization_version,
                   o.created_at AS organization_created_at, o.updated_at AS organization_updated_at,
                   m.id AS actor_membership_id, m.role AS actor_role
              FROM environments e
              JOIN projects p ON p.id = e.project_id AND p.organization_id = e.organization_id
              JOIN organizations o ON o.id = p.organization_id
              JOIN organization_memberships m ON m.organization_id = o.id
             WHERE e.id = ? AND m.oidc_issuer = ? AND m.oidc_subject = ?
            """
                + locking,
            (resultSet, rowNumber) ->
                new ScopedEnvironment(
                    mapAccess(resultSet), mapProject(resultSet), mapEnvironment(resultSet)),
            environmentId.value(),
            actor.issuer(),
            actor.subject());
    return matches.stream().findFirst();
  }

  private Optional<ScopedFlag> scopedFlag(OidcIdentity actor, FlagId flagId, boolean lock) {
    String locking = lock ? " FOR UPDATE OF p, f" : "";
    List<FlagHeaderScope> matches =
        jdbcTemplate.query(
            """
            SELECT f.*, p.id AS project_id_value, p.organization_id, p.project_key,
                   p.name AS project_name,
                   p.description, p.status AS project_status, p.version AS project_version,
                   p.created_at AS project_created_at, p.updated_at AS project_updated_at,
                   o.slug AS organization_slug, o.name AS organization_name,
                   o.status AS organization_status, o.version AS organization_version,
                   o.created_at AS organization_created_at, o.updated_at AS organization_updated_at,
                   m.id AS actor_membership_id, m.role AS actor_role
              FROM flags f
              JOIN projects p ON p.id = f.project_id AND p.organization_id = f.organization_id
              JOIN organizations o ON o.id = p.organization_id
              JOIN organization_memberships m ON m.organization_id = o.id
             WHERE f.id = ? AND m.oidc_issuer = ? AND m.oidc_subject = ?
            """
                + locking,
            (resultSet, rowNumber) ->
                new FlagHeaderScope(
                    mapAccess(resultSet), mapProject(resultSet), mapFlagHeader(resultSet)),
            flagId.value(),
            actor.issuer(),
            actor.subject());
    if (matches.isEmpty()) {
      return Optional.empty();
    }
    FlagHeaderScope header = matches.getFirst();
    List<Variation> variations = findVariations(header.flag().id(), header.flag().type());
    return Optional.of(
        new ScopedFlag(header.access(), header.project(), header.flag().build(variations)));
  }

  private List<Variation> findVariations(FlagId flagId, FlagType type) {
    return jdbcTemplate.query(
        """
        SELECT id, variation_key, name, canonical_value
          FROM flag_variations
         WHERE flag_id = ?
         ORDER BY position
        """,
        (resultSet, rowNumber) -> mapVariation(resultSet, type),
        flagId.value());
  }

  private List<FlagDefinition> extractFlags(ResultSet resultSet) throws SQLException {
    Map<UUID, FlagBuilder> builders = new LinkedHashMap<>();
    while (resultSet.next()) {
      UUID id = resultSet.getObject("id", UUID.class);
      FlagBuilder builder = builders.computeIfAbsent(id, ignored -> mapFlagHeader(resultSet));
      builder.variations().add(mapVariation(resultSet, builder.type()));
    }
    return builders.values().stream().map(FlagBuilder::build).toList();
  }

  private RowMapper<Project> projectRowMapper() {
    return (resultSet, rowNumber) -> mapProject(resultSet);
  }

  private RowMapper<Environment> environmentRowMapper() {
    return (resultSet, rowNumber) -> mapEnvironment(resultSet);
  }

  private RowMapper<EnvironmentDraft> draftRowMapper() {
    return (resultSet, rowNumber) ->
        new EnvironmentDraft(
            new FlagId(resultSet.getObject("flag_id", UUID.class)),
            new EnvironmentId(resultSet.getObject("environment_id", UUID.class)),
            resultSet.getBoolean("enabled"),
            resultSet.getObject("fallthrough_variation_id", UUID.class),
            resultSet.getObject("off_variation_id", UUID.class),
            resultSet.getString("rollout_salt"),
            readRules(resultSet.getString("rules")),
            readRollout(resultSet.getString("rollout")),
            resultSet.getString("change_summary"),
            resultSet.getLong("version"));
  }

  private RowMapper<PublishedRevision> revisionRowMapper() {
    return (resultSet, rowNumber) ->
        new PublishedRevision(
            new EnvironmentId(resultSet.getObject("environment_id", UUID.class)),
            resultSet.getLong("revision"),
            nullableLong(resultSet, "source_revision"),
            resultSet.getString("checksum_sha256"),
            resultSet.getString("canonical_snapshot"),
            resultSet.getString("human_reason"),
            resultSet.getString("actor_subject"),
            instant(resultSet, "created_at"));
  }

  private static Project mapProject(ResultSet resultSet) throws SQLException {
    String name = column(resultSet, "project_name", "name");
    String status = column(resultSet, "project_status", "status");
    long version = longColumn(resultSet, "project_version", "version");
    return new Project(
        new ProjectId(
            resultSet.getObject(availableColumn(resultSet, "project_id_value", "id"), UUID.class)),
        new OrganizationId(resultSet.getObject("organization_id", UUID.class)),
        new ResourceKey(resultSet.getString("project_key")),
        name,
        resultSet.getString("description"),
        Project.Status.valueOf(status),
        version,
        instant(resultSet, availableColumn(resultSet, "project_created_at", "created_at")),
        instant(resultSet, availableColumn(resultSet, "project_updated_at", "updated_at")));
  }

  private static Environment mapEnvironment(ResultSet resultSet) throws SQLException {
    return new Environment(
        new EnvironmentId(resultSet.getObject("id", UUID.class)),
        new ProjectId(resultSet.getObject("project_id", UUID.class)),
        new ResourceKey(resultSet.getString("environment_key")),
        resultSet.getString("name"),
        Environment.Kind.valueOf(resultSet.getString("kind")),
        Environment.Status.valueOf(resultSet.getString("status")),
        resultSet.getLong("current_revision"),
        resultSet.getLong("version"),
        instant(resultSet, "created_at"),
        instant(resultSet, "updated_at"));
  }

  private static FlagBuilder mapFlagHeader(ResultSet resultSet) {
    try {
      return new FlagBuilder(
          new FlagId(resultSet.getObject("id", UUID.class)),
          new ProjectId(resultSet.getObject("project_id", UUID.class)),
          new ResourceKey(resultSet.getString("flag_key")),
          resultSet.getString("name"),
          FlagType.valueOf(resultSet.getString("flag_type")),
          resultSet.getBoolean("client_visible"),
          FlagDefinition.Status.valueOf(resultSet.getString("status")),
          resultSet.getLong("version"),
          instant(resultSet, "created_at"),
          instant(resultSet, "updated_at"),
          new ArrayList<>());
    } catch (SQLException exception) {
      throw new IllegalStateException("Flag row could not be mapped", exception);
    }
  }

  private static Variation mapVariation(ResultSet resultSet, FlagType type) throws SQLException {
    return new Variation(
        resultSet.getObject(availableColumn(resultSet, "variation_id", "id"), UUID.class),
        new ResourceKey(resultSet.getString("variation_key")),
        resultSet.getString(availableColumn(resultSet, "variation_name", "name")),
        new FlagValue(type, resultSet.getString("canonical_value")));
  }

  private static OrganizationAccess mapAccess(ResultSet resultSet) throws SQLException {
    Instant createdAt = instant(resultSet, "organization_created_at");
    Organization organization =
        new Organization(
            new OrganizationId(resultSet.getObject("organization_id", UUID.class)),
            new OrganizationSlug(resultSet.getString("organization_slug")),
            resultSet.getString("organization_name"),
            OrganizationStatus.valueOf(resultSet.getString("organization_status")),
            resultSet.getLong("organization_version"),
            createdAt,
            instant(resultSet, "organization_updated_at"));
    return new OrganizationAccess(
        organization,
        new MembershipId(resultSet.getObject("actor_membership_id", UUID.class)),
        OrganizationRole.valueOf(resultSet.getString("actor_role")));
  }

  private List<Rule> readRules(String json) {
    try {
      return objectMapper.readValue(json, RULE_LIST);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Stored draft rules cannot be decoded", exception);
    }
  }

  private PercentageRollout readRollout(String json) {
    if (json == null) {
      return null;
    }
    try {
      return objectMapper.readValue(json, PercentageRollout.class);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Stored rollout cannot be decoded", exception);
    }
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Draft cannot be encoded", exception);
    }
  }

  private String writeNullableJson(Object value) {
    return value == null ? null : writeJson(value);
  }

  private static AuditEvent auditEvent(ResultSet resultSet) throws SQLException {
    return new AuditEvent(
        resultSet.getObject("id", UUID.class),
        new OrganizationId(resultSet.getObject("organization_id", UUID.class)),
        resultSet.getObject("project_id", UUID.class),
        resultSet.getObject("environment_id", UUID.class),
        resultSet.getString("actor_subject"),
        resultSet.getString("action"),
        resultSet.getString("target_type"),
        resultSet.getObject("target_id", UUID.class),
        resultSet.getString("safe_summary"),
        resultSet.getString("human_reason"),
        nullableLong(resultSet, "from_revision"),
        nullableLong(resultSet, "to_revision"),
        resultSet.getObject("correlation_id", UUID.class),
        instant(resultSet, "created_at"));
  }

  private static Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private static Instant instant(ResultSet resultSet, String column) throws SQLException {
    return resultSet.getTimestamp(column).toInstant();
  }

  private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
    long value = resultSet.getLong(column);
    return resultSet.wasNull() ? null : value;
  }

  private static String column(ResultSet resultSet, String preferred, String fallback)
      throws SQLException {
    return resultSet.getString(availableColumn(resultSet, preferred, fallback));
  }

  private static long longColumn(ResultSet resultSet, String preferred, String fallback)
      throws SQLException {
    return resultSet.getLong(availableColumn(resultSet, preferred, fallback));
  }

  private static String availableColumn(ResultSet resultSet, String preferred, String fallback)
      throws SQLException {
    try {
      resultSet.findColumn(preferred);
      return preferred;
    } catch (SQLException exception) {
      resultSet.findColumn(fallback);
      return fallback;
    }
  }

  private static void requireScopedMutation(int affectedRows) {
    if (affectedRows != 1) {
      throw new ControlPlaneNotFoundException();
    }
  }

  private static void requireVersionedMutation(int affectedRows) {
    if (affectedRows != 1) {
      throw new StaleWriteException();
    }
  }

  private record FlagHeaderScope(OrganizationAccess access, Project project, FlagBuilder flag) {}

  private record FlagBuilder(
      FlagId id,
      ProjectId projectId,
      ResourceKey key,
      String name,
      FlagType type,
      boolean clientVisible,
      FlagDefinition.Status status,
      long version,
      Instant createdAt,
      Instant updatedAt,
      List<Variation> variations) {
    private FlagDefinition build() {
      return build(variations);
    }

    private FlagDefinition build(List<Variation> values) {
      return new FlagDefinition(
          id,
          projectId,
          key,
          name,
          type,
          clientVisible,
          values,
          status,
          version,
          createdAt,
          updatedAt);
    }
  }
}
