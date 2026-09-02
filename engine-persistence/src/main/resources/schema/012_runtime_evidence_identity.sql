ALTER TABLE runtime_run ADD COLUMN ontology_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE runtime_run ADD COLUMN ontology_definition_sha256 TEXT;
ALTER TABLE runtime_run ADD COLUMN replay_of_run_id TEXT;
ALTER TABLE runtime_run ADD COLUMN ontology_input_json TEXT NOT NULL DEFAULT 'null';
