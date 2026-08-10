package dev.launchforge.infrastructure.organization;

import dev.launchforge.application.organization.AuditWriter;
import dev.launchforge.application.organization.MembershipAuditAction;
import dev.launchforge.application.organization.MembershipRepository;
import dev.launchforge.application.organization.OrganizationAccess;
import dev.launchforge.application.organization.OrganizationAccessRepository;
import dev.launchforge.application.organization.OrganizationNotFoundException;
import dev.launchforge.domain.organization.MembershipId;
import dev.launchforge.domain.organization.MembershipRoster;
import dev.launchforge.domain.organization.OidcIdentity;
import dev.launchforge.domain.organization.Organization;
import dev.launchforge.domain.organization.OrganizationId;
import dev.launchforge.domain.organization.OrganizationMembership;
import dev.launchforge.domain.organization.OrganizationRole;
import dev.launchforge.domain.organization.OrganizationSlug;
import dev.launchforge.domain.organization.OrganizationStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOrganizationPersistence
    implements OrganizationAccessRepository, MembershipRepository, AuditWriter {
  private static final RowMapper<OrganizationAccess> ACCESS_ROW_MAPPER =
      JdbcOrganizationPersistence::mapAccess;
  private static final RowMapper<OrganizationMembership> MEMBERSHIP_ROW_MAPPER =
      JdbcOrganizationPersistence::mapMembership;

  private final JdbcTemplate jdbcTemplate;
  private final NamedParameterJdbcTemplate namedJdbcTemplate;
  private final Clock clock;

  public JdbcOrganizationPersistence(JdbcTemplate jdbcTemplate, Clock clock) {
    this.jdbcTemplate = jdbcTemplate;
    this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    this.clock = clock;
  }

  @Override
  public List<OrganizationAccess> findAllFor(OidcIdentity actor) {
    return jdbcTemplate.query(
        """
        SELECT o.id, o.slug, o.name, o.status, o.version, o.created_at, o.updated_at,
               m.id AS actor_membership_id, m.role AS actor_role
          FROM organizations o
          JOIN organization_memberships m ON m.organization_id = o.id
         WHERE m.oidc_issuer = ? AND m.oidc_subject = ?
         ORDER BY o.name, o.id
        """,
        ACCESS_ROW_MAPPER,
        actor.issuer(),
        actor.subject());
  }

  @Override
  public Optional<OrganizationAccess> findFor(OidcIdentity actor, OrganizationId organizationId) {
    List<OrganizationAccess> matches =
        jdbcTemplate.query(
            """
            SELECT o.id, o.slug, o.name, o.status, o.version, o.created_at, o.updated_at,
                   m.id AS actor_membership_id, m.role AS actor_role
              FROM organizations o
              JOIN organization_memberships m ON m.organization_id = o.id
             WHERE o.id = ? AND m.oidc_issuer = ? AND m.oidc_subject = ?
            """,
            ACCESS_ROW_MAPPER,
            organizationId.value(),
            actor.issuer(),
            actor.subject());
    return matches.stream().findFirst();
  }

  @Override
  public List<OrganizationMembership> findAll(OrganizationAccess access) {
    return jdbcTemplate.query(
        """
        SELECT m.id, m.organization_id, m.oidc_issuer, m.oidc_subject, m.role,
               m.created_at, m.updated_at
          FROM organization_memberships m
         WHERE m.organization_id = ?
           AND EXISTS (
             SELECT 1 FROM organization_memberships actor
              WHERE actor.id = ? AND actor.organization_id = m.organization_id)
         ORDER BY m.created_at, m.id
        """,
        MEMBERSHIP_ROW_MAPPER,
        access.organization().id().value(),
        access.actorMembershipId().value());
  }

  @Override
  public MembershipRoster lockRoster(OrganizationAccess access) {
    List<UUID> lockedOrganizations =
        jdbcTemplate.queryForList(
            """
            SELECT o.id
              FROM organizations o
             WHERE o.id = ?
               AND EXISTS (
                 SELECT 1 FROM organization_memberships actor
                  WHERE actor.id = ? AND actor.organization_id = o.id)
             FOR UPDATE
            """,
            UUID.class,
            access.organization().id().value(),
            access.actorMembershipId().value());
    if (lockedOrganizations.isEmpty()) {
      throw new OrganizationNotFoundException();
    }
    return new MembershipRoster(access.organization().id(), findAll(access));
  }

  @Override
  public void insert(OrganizationAccess access, OrganizationMembership membership) {
    MapSqlParameterSource parameters = membershipParameters(access, membership);
    int inserted =
        namedJdbcTemplate.update(
            """
            INSERT INTO organization_memberships
              (id, organization_id, oidc_issuer, oidc_subject, role, created_at, updated_at)
            SELECT :id, :organizationId, :issuer, :subject, :role, :createdAt, :updatedAt
             WHERE EXISTS (
               SELECT 1 FROM organization_memberships actor
                WHERE actor.id = :actorMembershipId
                  AND actor.organization_id = :organizationId)
            """,
            parameters);
    requireSingleScopedMutation(inserted);
  }

  @Override
  public void updateRole(OrganizationAccess access, OrganizationMembership membership) {
    MapSqlParameterSource parameters = membershipParameters(access, membership);
    int updated =
        namedJdbcTemplate.update(
            """
            UPDATE organization_memberships target
               SET role = :role, updated_at = :updatedAt
             WHERE target.id = :id
               AND target.organization_id = :organizationId
               AND EXISTS (
                 SELECT 1 FROM organization_memberships actor
                  WHERE actor.id = :actorMembershipId
                    AND actor.organization_id = target.organization_id)
            """,
            parameters);
    requireSingleScopedMutation(updated);
  }

  @Override
  public void delete(OrganizationAccess access, MembershipId membershipId) {
    int deleted =
        jdbcTemplate.update(
            """
            DELETE FROM organization_memberships target
             WHERE target.id = ?
               AND target.organization_id = ?
               AND EXISTS (
                 SELECT 1 FROM organization_memberships actor
                  WHERE actor.id = ? AND actor.organization_id = target.organization_id)
            """,
            membershipId.value(),
            access.organization().id().value(),
            access.actorMembershipId().value());
    requireSingleScopedMutation(deleted);
  }

  @Override
  public void appendMembershipEvent(
      OrganizationAccess access,
      OidcIdentity actor,
      MembershipAuditAction action,
      MembershipId targetMembershipId,
      String reasonCode) {
    jdbcTemplate.update(
        """
        INSERT INTO audit_events
          (id, organization_id, actor_issuer, actor_subject, action, target_type,
           target_id, reason_code, correlation_id, created_at)
        VALUES (?, ?, ?, ?, ?, 'ORGANIZATION_MEMBERSHIP', ?, ?, ?, ?)
        """,
        UUID.randomUUID(),
        access.organization().id().value(),
        actor.issuer(),
        actor.subject(),
        action.name(),
        targetMembershipId.value(),
        reasonCode,
        UUID.randomUUID(),
        Timestamp.from(clock.instant()));
  }

  private static MapSqlParameterSource membershipParameters(
      OrganizationAccess access, OrganizationMembership membership) {
    return new MapSqlParameterSource(
        Map.of(
            "id", membership.id().value(),
            "organizationId", membership.organizationId().value(),
            "issuer", membership.identity().issuer(),
            "subject", membership.identity().subject(),
            "role", membership.role().name(),
            "createdAt", Timestamp.from(membership.createdAt()),
            "updatedAt", Timestamp.from(membership.updatedAt()),
            "actorMembershipId", access.actorMembershipId().value()));
  }

  private static OrganizationAccess mapAccess(ResultSet resultSet, int rowNumber)
      throws SQLException {
    Organization organization =
        new Organization(
            new OrganizationId(resultSet.getObject("id", UUID.class)),
            new OrganizationSlug(resultSet.getString("slug")),
            resultSet.getString("name"),
            OrganizationStatus.valueOf(resultSet.getString("status")),
            resultSet.getLong("version"),
            instant(resultSet, "created_at"),
            instant(resultSet, "updated_at"));
    return new OrganizationAccess(
        organization,
        new MembershipId(resultSet.getObject("actor_membership_id", UUID.class)),
        OrganizationRole.valueOf(resultSet.getString("actor_role")));
  }

  private static OrganizationMembership mapMembership(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new OrganizationMembership(
        new MembershipId(resultSet.getObject("id", UUID.class)),
        new OrganizationId(resultSet.getObject("organization_id", UUID.class)),
        new OidcIdentity(resultSet.getString("oidc_issuer"), resultSet.getString("oidc_subject")),
        OrganizationRole.valueOf(resultSet.getString("role")),
        instant(resultSet, "created_at"),
        instant(resultSet, "updated_at"));
  }

  private static Instant instant(ResultSet resultSet, String column) throws SQLException {
    return resultSet.getTimestamp(column).toInstant();
  }

  private static void requireSingleScopedMutation(int affectedRows) {
    if (affectedRows != 1) {
      throw new OrganizationNotFoundException();
    }
  }
}
