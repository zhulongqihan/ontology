ALTER TABLE runtime_run ADD COLUMN duration_ns INTEGER NOT NULL DEFAULT 0;
ALTER TABLE trace ADD COLUMN duration_ns INTEGER NOT NULL DEFAULT 0;
ALTER TABLE trace_span ADD COLUMN duration_ns INTEGER NOT NULL DEFAULT 0;

UPDATE runtime_run SET duration_ns = duration_ms * 1000000 WHERE duration_ns = 0 AND duration_ms > 0;
UPDATE trace SET duration_ns = duration_ms * 1000000 WHERE duration_ns = 0 AND duration_ms > 0;
UPDATE trace_span SET duration_ns = duration_ms * 1000000 WHERE duration_ns = 0 AND duration_ms > 0;
