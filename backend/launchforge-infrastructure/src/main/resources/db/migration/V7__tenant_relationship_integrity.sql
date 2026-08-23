ALTER TABLE audit_events
    ADD CONSTRAINT audit_events_environment_requires_project
        CHECK (environment_id IS NULL OR project_id IS NOT NULL),
    ADD CONSTRAINT audit_events_project_fk
        FOREIGN KEY (organization_id, project_id)
        REFERENCES projects(organization_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT audit_events_environment_fk
        FOREIGN KEY (organization_id, project_id, environment_id)
        REFERENCES environments(organization_id, project_id, id) ON DELETE RESTRICT;

ALTER TABLE sdk_keys
    ADD CONSTRAINT sdk_keys_scoped_id_unique
        UNIQUE (organization_id, project_id, environment_id, id),
    DROP CONSTRAINT sdk_keys_rotated_from_fk,
    ADD CONSTRAINT sdk_keys_rotated_from_fk
        FOREIGN KEY (organization_id, project_id, environment_id, rotated_from_id)
        REFERENCES sdk_keys(organization_id, project_id, environment_id, id)
        ON DELETE RESTRICT;
