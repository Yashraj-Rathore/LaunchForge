CREATE TABLE audit_retention_previews (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    delete_before TIMESTAMPTZ NOT NULL,
    candidate_ids JSONB NOT NULL,
    candidate_count INTEGER NOT NULL,
    created_by_issuer VARCHAR(2048) NOT NULL,
    created_by_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    applied_at TIMESTAMPTZ,
    CONSTRAINT audit_retention_previews_organization_fk
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
    CONSTRAINT audit_retention_previews_candidate_ids_array
        CHECK (jsonb_typeof(candidate_ids) = 'array'),
    CONSTRAINT audit_retention_previews_candidate_count_nonnegative
        CHECK (candidate_count >= 0),
    CONSTRAINT audit_retention_previews_candidate_count_matches
        CHECK (candidate_count = jsonb_array_length(candidate_ids)),
    CONSTRAINT audit_retention_previews_time_order
        CHECK (expires_at > created_at),
    CONSTRAINT audit_retention_previews_apply_order
        CHECK (applied_at IS NULL OR applied_at >= created_at)
);

CREATE INDEX audit_retention_previews_organization_time_idx
    ON audit_retention_previews (organization_id, created_at DESC);

CREATE FUNCTION enforce_audit_event_immutability()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    preview_id_text TEXT;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'audit events are immutable' USING ERRCODE = '55000';
    END IF;

    preview_id_text := current_setting('launchforge.audit_retention_preview_id', TRUE);
    IF preview_id_text IS NULL OR preview_id_text = '' THEN
        RAISE EXCEPTION 'audit events can only be deleted by an approved retention preview'
            USING ERRCODE = '55000';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM audit_retention_previews preview
         WHERE preview.id = preview_id_text::UUID
           AND preview.organization_id = OLD.organization_id
           AND preview.applied_at IS NULL
           AND preview.expires_at >= CURRENT_TIMESTAMP
           AND OLD.created_at < preview.delete_before
           AND preview.candidate_ids ? OLD.id::TEXT
    ) THEN
        RAISE EXCEPTION 'audit event is outside the approved retention preview'
            USING ERRCODE = '55000';
    END IF;

    RETURN OLD;
END;
$$;

CREATE TRIGGER audit_events_immutable
BEFORE UPDATE OR DELETE ON audit_events
FOR EACH ROW EXECUTE FUNCTION enforce_audit_event_immutability();
