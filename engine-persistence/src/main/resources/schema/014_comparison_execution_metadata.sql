ALTER TABLE runtime_run ADD COLUMN execution_mode TEXT NOT NULL DEFAULT 'FLEXIBLE_ENGINE';
ALTER TABLE runtime_run ADD COLUMN comparison_id TEXT;
ALTER TABLE runtime_run ADD COLUMN paired_run_id TEXT;
ALTER TABLE runtime_run ADD COLUMN case_id TEXT;
ALTER TABLE runtime_run ADD COLUMN input_sha256 TEXT;
ALTER TABLE runtime_run ADD COLUMN configuration_sha256 TEXT;
