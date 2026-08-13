package dev.launchforge.eventworker.projection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthoritativeSnapshotRepository {
  private final JdbcTemplate jdbcTemplate;

  public AuthoritativeSnapshotRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<AuthoritativeSnapshot> findRevision(UUID environmentId, long revision) {
    List<AuthoritativeSnapshot> results =
        jdbcTemplate.query(
            """
            SELECT e.organization_id, e.project_id, e.id AS environment_id,
                   r.revision, e.current_revision, r.schema_version,
                   r.canonical_snapshot, r.checksum_sha256
              FROM environments e
              JOIN projects p ON p.id = e.project_id AND p.organization_id = e.organization_id
              JOIN organizations o ON o.id = e.organization_id
              JOIN environment_revisions r
                ON r.environment_id = e.id AND r.revision = ?
             WHERE e.id = ? AND e.status = 'ACTIVE'
               AND p.status = 'ACTIVE' AND o.status = 'ACTIVE'
            """,
            AuthoritativeSnapshotRepository::map,
            revision,
            environmentId);
    return results.stream().findFirst();
  }

  public List<AuthoritativeSnapshot> findCurrentPage(UUID afterEnvironmentId, int limit) {
    return jdbcTemplate.query(
        """
        SELECT e.organization_id, e.project_id, e.id AS environment_id,
               r.revision, e.current_revision, r.schema_version,
               r.canonical_snapshot, r.checksum_sha256
          FROM environments e
          JOIN projects p ON p.id = e.project_id AND p.organization_id = e.organization_id
          JOIN organizations o ON o.id = e.organization_id
          JOIN environment_revisions r
            ON r.environment_id = e.id AND r.revision = e.current_revision
         WHERE (?::uuid IS NULL OR e.id > ?::uuid)
           AND e.current_revision > 0
           AND e.status = 'ACTIVE' AND p.status = 'ACTIVE' AND o.status = 'ACTIVE'
         ORDER BY e.id
         LIMIT ?
        """,
        AuthoritativeSnapshotRepository::map,
        afterEnvironmentId,
        afterEnvironmentId,
        limit);
  }

  private static AuthoritativeSnapshot map(java.sql.ResultSet resultSet, int rowNumber)
      throws java.sql.SQLException {
    return new AuthoritativeSnapshot(
        resultSet.getObject("organization_id", UUID.class),
        resultSet.getObject("project_id", UUID.class),
        resultSet.getObject("environment_id", UUID.class),
        resultSet.getLong("revision"),
        resultSet.getLong("current_revision"),
        resultSet.getInt("schema_version"),
        resultSet.getString("canonical_snapshot"),
        resultSet.getString("checksum_sha256"));
  }
}
