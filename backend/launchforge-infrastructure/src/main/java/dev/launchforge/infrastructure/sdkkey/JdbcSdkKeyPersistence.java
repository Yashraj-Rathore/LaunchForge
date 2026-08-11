package dev.launchforge.infrastructure.sdkkey;

import dev.launchforge.application.organization.OrganizationAccess;
import dev.launchforge.application.sdkkey.SdkKeyRepository;
import dev.launchforge.domain.controlplane.Environment;
import dev.launchforge.domain.controlplane.EnvironmentId;
import dev.launchforge.domain.controlplane.ProjectId;
import dev.launchforge.domain.organization.MembershipId;
import dev.launchforge.domain.organization.OidcIdentity;
import dev.launchforge.domain.organization.Organization;
import dev.launchforge.domain.organization.OrganizationId;
import dev.launchforge.domain.organization.OrganizationRole;
import dev.launchforge.domain.organization.OrganizationSlug;
import dev.launchforge.domain.organization.OrganizationStatus;
import dev.launchforge.domain.sdkkey.SdkKeyId;
import dev.launchforge.domain.sdkkey.SdkKeyStatus;
import dev.launchforge.domain.sdkkey.ServerSdkKey;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSdkKeyPersistence implements SdkKeyRepository {
  private final JdbcTemplate jdbcTemplate;

  public JdbcSdkKeyPersistence(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public List<ServerSdkKey> findForEnvironment(OrganizationAccess access, Environment environment) {
    return jdbcTemplate.query(
        """
        SELECT k.*
          FROM sdk_keys k
         WHERE k.organization_id = ? AND k.environment_id = ?
           AND EXISTS (
             SELECT 1 FROM organization_memberships actor
              WHERE actor.id = ? AND actor.organization_id = k.organization_id)
         ORDER BY k.created_at DESC, k.id
        """,
        (resultSet, rowNumber) -> mapKey(resultSet),
        access.organization().id().value(),
        environment.id().value(),
        access.actorMembershipId().value());
  }

  @Override
  public Optional<ScopedSdkKey> lockFor(OidcIdentity actor, SdkKeyId keyId) {
    List<ScopedSdkKey> matches =
        jdbcTemplate.query(
            """
            SELECT k.*, o.slug AS organization_slug, o.name AS organization_name,
                   o.status AS organization_status, o.version AS organization_version,
                   o.created_at AS organization_created_at,
                   o.updated_at AS organization_updated_at,
                   m.id AS actor_membership_id, m.role AS actor_role
              FROM sdk_keys k
              JOIN organizations o ON o.id = k.organization_id
              JOIN organization_memberships m ON m.organization_id = o.id
             WHERE k.id = ? AND m.oidc_issuer = ? AND m.oidc_subject = ?
             FOR UPDATE OF k
            """,
            (resultSet, rowNumber) -> new ScopedSdkKey(mapAccess(resultSet), mapKey(resultSet)),
            keyId.value(),
            actor.issuer(),
            actor.subject());
    return matches.stream().findFirst();
  }

  @Override
  public void insert(
      OrganizationAccess access,
      OidcIdentity actor,
      ServerSdkKey key,
      byte[] verifier,
      String auditAction) {
    int inserted =
        jdbcTemplate.update(
            """
            INSERT INTO sdk_keys
              (id, organization_id, project_id, environment_id, key_type, name, lookup_id,
               secret_verifier, pepper_version, fingerprint, status, expires_at, created_at,
               created_by_issuer, created_by_subject, rotated_from_id)
            SELECT ?, ?, ?, ?, 'SERVER', ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?
             WHERE EXISTS (
               SELECT 1 FROM organization_memberships actor
                WHERE actor.id = ? AND actor.organization_id = ?)
            """,
            key.id().value(),
            key.organizationId().value(),
            key.projectId().value(),
            key.environmentId().value(),
            key.name(),
            key.lookupId(),
            verifier,
            key.pepperVersion(),
            key.fingerprint(),
            timestamp(key.expiresAt()),
            Timestamp.from(key.createdAt()),
            actor.issuer(),
            actor.subject(),
            key.rotatedFromId() == null ? null : key.rotatedFromId().value(),
            access.actorMembershipId().value(),
            access.organization().id().value());
    requireOne(inserted);
    appendAudit(access, actor, key, auditAction, key.createdAt());
  }

  @Override
  public void endValidity(
      OrganizationAccess access,
      OidcIdentity actor,
      ServerSdkKey key,
      Instant expiresAt,
      boolean revoke,
      String auditAction) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE sdk_keys k
               SET status = CASE WHEN ? THEN 'REVOKED' ELSE status END,
                   expires_at = ?, revoked_at = CASE WHEN ? THEN ? ELSE revoked_at END,
                   revoked_by_issuer = CASE WHEN ? THEN ? ELSE revoked_by_issuer END,
                   revoked_by_subject = CASE WHEN ? THEN ? ELSE revoked_by_subject END
             WHERE k.id = ? AND k.organization_id = ?
               AND EXISTS (
                 SELECT 1 FROM organization_memberships actor
                  WHERE actor.id = ? AND actor.organization_id = k.organization_id)
            """,
            revoke,
            Timestamp.from(expiresAt),
            revoke,
            Timestamp.from(expiresAt),
            revoke,
            actor.issuer(),
            revoke,
            actor.subject(),
            key.id().value(),
            access.organization().id().value(),
            access.actorMembershipId().value());
    requireOne(updated);
    appendAudit(access, actor, key, auditAction, expiresAt);
  }

  private void appendAudit(
      OrganizationAccess access,
      OidcIdentity actor,
      ServerSdkKey key,
      String action,
      Instant occurredAt) {
    int inserted =
        jdbcTemplate.update(
            """
            INSERT INTO audit_events
              (id, organization_id, project_id, environment_id, actor_issuer, actor_subject,
               action, target_type, target_id, reason_code, safe_summary, correlation_id, created_at)
            SELECT ?, ?, ?, ?, ?, ?, ?, 'SDK_KEY', ?, 'SUCCESS', ?, ?, ?
             WHERE EXISTS (
               SELECT 1 FROM organization_memberships actor
                WHERE actor.id = ? AND actor.organization_id = ?)
            """,
            UUID.randomUUID(),
            access.organization().id().value(),
            key.projectId().value(),
            key.environmentId().value(),
            actor.issuer(),
            actor.subject(),
            action,
            key.id().value(),
            action.replace('_', ' ').toLowerCase(Locale.ROOT),
            UUID.randomUUID(),
            Timestamp.from(occurredAt),
            access.actorMembershipId().value(),
            access.organization().id().value());
    requireOne(inserted);
  }

  private static ServerSdkKey mapKey(ResultSet resultSet) throws SQLException {
    return new ServerSdkKey(
        new SdkKeyId(resultSet.getObject("id", UUID.class)),
        new OrganizationId(resultSet.getObject("organization_id", UUID.class)),
        new ProjectId(resultSet.getObject("project_id", UUID.class)),
        new EnvironmentId(resultSet.getObject("environment_id", UUID.class)),
        resultSet.getString("name"),
        resultSet.getString("lookup_id"),
        resultSet.getString("fingerprint"),
        resultSet.getString("pepper_version"),
        SdkKeyStatus.valueOf(resultSet.getString("status")),
        instant(resultSet, "expires_at"),
        resultSet.getTimestamp("created_at").toInstant(),
        instant(resultSet, "last_used_at"),
        instant(resultSet, "revoked_at"),
        resultSet.getObject("rotated_from_id", UUID.class) == null
            ? null
            : new SdkKeyId(resultSet.getObject("rotated_from_id", UUID.class)));
  }

  private static OrganizationAccess mapAccess(ResultSet resultSet) throws SQLException {
    Organization organization =
        new Organization(
            new OrganizationId(resultSet.getObject("organization_id", UUID.class)),
            new OrganizationSlug(resultSet.getString("organization_slug")),
            resultSet.getString("organization_name"),
            OrganizationStatus.valueOf(resultSet.getString("organization_status")),
            resultSet.getLong("organization_version"),
            resultSet.getTimestamp("organization_created_at").toInstant(),
            resultSet.getTimestamp("organization_updated_at").toInstant());
    return new OrganizationAccess(
        organization,
        new MembershipId(resultSet.getObject("actor_membership_id", UUID.class)),
        OrganizationRole.valueOf(resultSet.getString("actor_role")));
  }

  private static Instant instant(ResultSet resultSet, String column) throws SQLException {
    Timestamp value = resultSet.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  private static Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private static void requireOne(int affected) {
    if (affected != 1) {
      throw new IllegalStateException("Expected one tenant-scoped SDK key row");
    }
  }
}
