ALTER TABLE runtime_run ADD COLUMN input_values_json TEXT NOT NULL DEFAULT '{}';
ALTER TABLE runtime_run ADD COLUMN retry_of_run_id TEXT;
ALTER TABLE runtime_run ADD COLUMN attempt INTEGER NOT NULL DEFAULT 1;
