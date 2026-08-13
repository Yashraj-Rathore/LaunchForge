package dev.launchforge.eventworker.outbox;

import dev.launchforge.contracts.events.ConfigRevisionPublishedEvent;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOutboxRepository {
  private final JdbcTemplate jdbcTemplate;

  public JdbcOutboxRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<OutboxEvent> lease(String owner, int batchSize, Duration leaseDuration) {
    return jdbcTemplate.query(
        """
        WITH claimable AS (
          SELECT id
            FROM outbox_events
           WHERE event_type = ?
             AND ((status = 'PENDING' AND available_at <= clock_timestamp())
               OR (status = 'PROCESSING' AND lease_until <= clock_timestamp()))
           ORDER BY created_at, id
           FOR UPDATE SKIP LOCKED
           LIMIT ?
        )
        UPDATE outbox_events event
           SET status = 'PROCESSING',
               attempt_count = attempt_count + 1,
               lease_owner = ?,
               lease_until = clock_timestamp() + (? * INTERVAL '1 millisecond'),
               last_error_code = NULL
          FROM claimable
         WHERE event.id = claimable.id
        RETURNING event.id, event.aggregate_id, event.aggregate_revision,
                  event.payload::text AS payload, event.attempt_count
        """,
        (resultSet, rowNumber) ->
            new OutboxEvent(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("aggregate_id", UUID.class),
                resultSet.getLong("aggregate_revision"),
                resultSet.getString("payload"),
                resultSet.getInt("attempt_count")),
        ConfigRevisionPublishedEvent.EVENT_TYPE,
        batchSize,
        owner,
        leaseDuration.toMillis());
  }

  public boolean markPublished(UUID eventId, String owner) {
    return jdbcTemplate.update(
            """
            UPDATE outbox_events
               SET status = 'PUBLISHED', published_at = clock_timestamp(),
                   lease_owner = NULL, lease_until = NULL, last_error_code = NULL
             WHERE id = ? AND status = 'PROCESSING' AND lease_owner = ?
            """,
            eventId,
            owner)
        == 1;
  }

  public boolean releaseForRetry(
      UUID eventId, String owner, Duration retryDelay, String errorCode) {
    return jdbcTemplate.update(
            """
            UPDATE outbox_events
               SET status = 'PENDING',
                   available_at = clock_timestamp() + (? * INTERVAL '1 millisecond'),
                   lease_owner = NULL, lease_until = NULL, last_error_code = ?
             WHERE id = ? AND status = 'PROCESSING' AND lease_owner = ?
            """,
            retryDelay.toMillis(),
            errorCode,
            eventId,
            owner)
        == 1;
  }

  public boolean markFailed(UUID eventId, String owner, String errorCode) {
    return jdbcTemplate.update(
            """
            UPDATE outbox_events
               SET status = 'FAILED', lease_owner = NULL, lease_until = NULL,
                   last_error_code = ?
             WHERE id = ? AND status = 'PROCESSING' AND lease_owner = ?
            """,
            errorCode,
            eventId,
            owner)
        == 1;
  }

  public OutboxStats stats() {
    return jdbcTemplate.queryForObject(
        """
        SELECT count(*) AS pending_count,
               COALESCE(EXTRACT(EPOCH FROM (clock_timestamp() - min(created_at))), 0)
                 AS oldest_age_seconds
          FROM outbox_events
         WHERE status IN ('PENDING', 'PROCESSING')
        """,
        (resultSet, rowNumber) ->
            new OutboxStats(
                resultSet.getLong("pending_count"), resultSet.getDouble("oldest_age_seconds")));
  }
}
