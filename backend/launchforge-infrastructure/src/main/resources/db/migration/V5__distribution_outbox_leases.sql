ALTER TABLE outbox_events
    ADD COLUMN lease_owner VARCHAR(64),
    ADD COLUMN lease_until TIMESTAMPTZ,
    ADD CONSTRAINT outbox_events_lease_pair_valid CHECK (
        (status = 'PROCESSING' AND lease_owner IS NOT NULL AND lease_until IS NOT NULL)
        OR
        (status <> 'PROCESSING' AND lease_owner IS NULL AND lease_until IS NULL)
    );

CREATE INDEX outbox_events_claimable_idx
    ON outbox_events (available_at, lease_until, created_at)
    WHERE status IN ('PENDING', 'PROCESSING');
