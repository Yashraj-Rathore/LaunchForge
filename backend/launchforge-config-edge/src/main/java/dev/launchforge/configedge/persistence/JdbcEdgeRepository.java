package dev.launchforge.configedge.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcEdgeRepository implements EdgeRepository {
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public JdbcEdgeRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<StoredSdkCredential> findCredential(String lookupId) {
    List<StoredSdkCredential> matches =
        jdbcTemplate.query(
            """
            SELECT k.id, k.environment_id, k.secret_verifier, k.pepper_version, k.status,
                   k.expires_at,
                   (o.status = 'ACTIVE' AND p.status = 'ACTIVE' AND e.status = 'ACTIVE')
                     AS scope_active
              FROM sdk_keys k
              JOIN organizations o ON o.id = k.organization_id
              JOIN projects p ON p.id = k.project_id AND p.organization_id = k.organization_id
              JOIN environments e ON e.id = k.environment_id
                 AND e.project_id = k.project_id AND e.organization_id = k.organization_id
             WHERE k.lookup_id = ? AND k.key_type = 'SERVER'
            """,
            (resultSet, rowNumber) ->
                new StoredSdkCredential(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("environment_id", UUID.class),
                    resultSet.getBytes("secret_verifier"),
                    resultSet.getString("pepper_version"),
                    resultSet.getString("status"),
                    instant(resultSet.getTimestamp("expires_at")),
                    resultSet.getBoolean("scope_active")),
            lookupId);
    return matches.stream().findFirst();
  }

  @Override
  public Optional<CredentialLifecycle> findLifecycle(UUID keyId) {
    List<CredentialLifecycle> matches =
        jdbcTemplate.query(
            """
            SELECT k.environment_id, k.status, k.expires_at,
                   (o.status = 'ACTIVE' AND p.status = 'ACTIVE' AND e.status = 'ACTIVE')
                     AS scope_active
              FROM sdk_keys k
              JOIN organizations o ON o.id = k.organization_id
              JOIN projects p ON p.id = k.project_id AND p.organization_id = k.organization_id
              JOIN environments e ON e.id = k.environment_id
                 AND e.project_id = k.project_id AND e.organization_id = k.organization_id
             WHERE k.id = ? AND k.key_type = 'SERVER'
            """,
            (resultSet, rowNumber) ->
                new CredentialLifecycle(
                    resultSet.getObject("environment_id", UUID.class),
                    resultSet.getString("status"),
                    instant(resultSet.getTimestamp("expires_at")),
                    resultSet.getBoolean("scope_active")),
            keyId);
    return matches.stream().findFirst();
  }

  @Override
  public Optional<StoredSnapshot> findCurrentSnapshot(UUID environmentId) {
    List<StoredSnapshot> matches =
        jdbcTemplate.query(
            """
            SELECT e.id AS environment_id, r.revision, r.schema_version,
                   r.canonical_snapshot, r.checksum_sha256
              FROM environments e
              JOIN projects p ON p.id = e.project_id AND p.organization_id = e.organization_id
              JOIN organizations o ON o.id = e.organization_id
              JOIN environment_revisions r
                ON r.environment_id = e.id AND r.revision = e.current_revision
             WHERE e.id = ? AND e.status = 'ACTIVE'
               AND p.status = 'ACTIVE' AND o.status = 'ACTIVE'
            """,
            (resultSet, rowNumber) ->
                new StoredSnapshot(
                    resultSet.getObject("environment_id", UUID.class),
                    resultSet.getLong("revision"),
                    resultSet.getInt("schema_version"),
                    resultSet.getString("canonical_snapshot"),
                    resultSet.getString("checksum_sha256")),
            environmentId);
    return matches.stream().findFirst();
  }

  @Override
  public OptionalLong findCurrentRevision(UUID environmentId) {
    List<Long> revisions =
        jdbcTemplate.query(
            """
            SELECT e.current_revision
              FROM environments e
              JOIN projects p ON p.id = e.project_id AND p.organization_id = e.organization_id
              JOIN organizations o ON o.id = e.organization_id
             WHERE e.id = ? AND e.status = 'ACTIVE'
               AND p.status = 'ACTIVE' AND o.status = 'ACTIVE'
            """,
            (resultSet, rowNumber) -> resultSet.getLong("current_revision"),
            environmentId);
    return revisions.isEmpty() ? OptionalLong.empty() : OptionalLong.of(revisions.getFirst());
  }

  @Override
  public void recordUse(UUID keyId) {
    jdbcTemplate.update(
        """
        UPDATE sdk_keys
           SET last_used_at = CURRENT_TIMESTAMP
         WHERE id = ?
           AND (last_used_at IS NULL OR last_used_at < CURRENT_TIMESTAMP - INTERVAL '1 hour')
        """,
        keyId);
  }

  @Override
  public Optional<EnvironmentScope> findEnvironmentScope(UUID environmentId) {
    List<EnvironmentScope> matches =
        jdbcTemplate.query(
            """
            SELECT e.organization_id, e.project_id, e.id AS environment_id,
                   p.project_key, e.environment_key
              FROM environments e
              JOIN projects p ON p.id = e.project_id AND p.organization_id = e.organization_id
              JOIN organizations o ON o.id = e.organization_id
             WHERE e.id = ? AND e.status = 'ACTIVE'
               AND p.status = 'ACTIVE' AND o.status = 'ACTIVE'
            """,
            (resultSet, rowNumber) ->
                new EnvironmentScope(
                    resultSet.getObject("organization_id", UUID.class),
                    resultSet.getObject("project_id", UUID.class),
                    resultSet.getObject("environment_id", UUID.class),
                    resultSet.getString("project_key"),
                    resultSet.getString("environment_key")),
            environmentId);
    return matches.stream().findFirst();
  }

  @Override
  public Optional<StoredBrowserCredential> findBrowserCredential(String clientKey) {
    List<StoredBrowserCredential> matches =
        jdbcTemplate.query(
            """
            SELECT k.id, k.environment_id, k.allowed_origins, k.status, k.expires_at,
                   (o.status = 'ACTIVE' AND p.status = 'ACTIVE' AND e.status = 'ACTIVE')
                     AS scope_active
              FROM browser_client_keys k
              JOIN organizations o ON o.id = k.organization_id
              JOIN projects p ON p.id = k.project_id AND p.organization_id = k.organization_id
              JOIN environments e ON e.id = k.environment_id
                 AND e.project_id = k.project_id AND e.organization_id = k.organization_id
             WHERE k.client_key = ?
            """,
            (resultSet, rowNumber) ->
                new StoredBrowserCredential(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("environment_id", UUID.class),
                    origins(resultSet.getString("allowed_origins")),
                    resultSet.getString("status"),
                    instant(resultSet.getTimestamp("expires_at")),
                    resultSet.getBoolean("scope_active")),
            clientKey);
    return matches.stream().findFirst();
  }

  @Override
  public Optional<BrowserCredentialLifecycle> findBrowserLifecycle(UUID keyId) {
    List<BrowserCredentialLifecycle> matches =
        jdbcTemplate.query(
            """
            SELECT k.environment_id, k.status, k.expires_at,
                   (o.status = 'ACTIVE' AND p.status = 'ACTIVE' AND e.status = 'ACTIVE')
                     AS scope_active
              FROM browser_client_keys k
              JOIN organizations o ON o.id = k.organization_id
              JOIN projects p ON p.id = k.project_id AND p.organization_id = k.organization_id
              JOIN environments e ON e.id = k.environment_id
                 AND e.project_id = k.project_id AND e.organization_id = k.organization_id
             WHERE k.id = ?
            """,
            (resultSet, rowNumber) ->
                new BrowserCredentialLifecycle(
                    resultSet.getObject("environment_id", UUID.class),
                    resultSet.getString("status"),
                    instant(resultSet.getTimestamp("expires_at")),
                    resultSet.getBoolean("scope_active")),
            keyId);
    return matches.stream().findFirst();
  }

  @Override
  public void recordBrowserUse(UUID keyId) {
    jdbcTemplate.update(
        """
        UPDATE browser_client_keys
           SET last_used_at = CURRENT_TIMESTAMP
         WHERE id = ?
           AND (last_used_at IS NULL OR last_used_at < CURRENT_TIMESTAMP - INTERVAL '1 hour')
        """,
        keyId);
  }

  private List<String> origins(String json) {
    try {
      JsonNode node = objectMapper.readTree(json);
      java.util.ArrayList<String> origins = new java.util.ArrayList<>();
      node.forEach(value -> origins.add(value.stringValue()));
      return origins;
    } catch (JacksonException exception) {
      throw new IllegalStateException("Stored browser origin policy is invalid", exception);
    }
  }

  private static Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }
}
