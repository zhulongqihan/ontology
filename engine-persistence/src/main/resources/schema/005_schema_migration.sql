CREATE TABLE IF NOT EXISTS schema_migration (
    model_id TEXT NOT NULL,
    from_version INTEGER NOT NULL,
    to_version INTEGER NOT NULL,
    source_field TEXT NOT NULL,
    target_field TEXT NOT NULL,
    created_at TEXT NOT NULL,
    PRIMARY KEY(model_id, from_version, to_version, source_field, target_field),
    FOREIGN KEY(model_id, from_version) REFERENCES schema_definition(model_id, schema_version) ON DELETE CASCADE,
    FOREIGN KEY(model_id, to_version) REFERENCES schema_definition(model_id, schema_version) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_schema_migration_model ON schema_migration(model_id, from_version, to_version);
