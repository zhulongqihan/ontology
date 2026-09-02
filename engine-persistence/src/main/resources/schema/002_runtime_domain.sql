CREATE TABLE IF NOT EXISTS runtime_context (
    context_id TEXT NOT NULL PRIMARY KEY,
    model_id TEXT NOT NULL,
    schema_version INTEGER NOT NULL,
    workflow_version INTEGER NOT NULL,
    state TEXT NOT NULL,
    status TEXT NOT NULL,
    revision INTEGER NOT NULL,
    last_run_id TEXT,
    last_snapshot_sha256 TEXT,
    values_json TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_runtime_context_model ON runtime_context(model_id);

CREATE TABLE IF NOT EXISTS runtime_run (
    run_id TEXT NOT NULL PRIMARY KEY,
    model_id TEXT NOT NULL,
    context_id TEXT NOT NULL,
    engine_version TEXT NOT NULL,
    schema_version INTEGER NOT NULL,
    workflow_version INTEGER NOT NULL,
    status TEXT NOT NULL,
    data_identity TEXT NOT NULL,
    event TEXT NOT NULL,
    from_state TEXT NOT NULL,
    to_state TEXT NOT NULL,
    trace_id TEXT NOT NULL,
    idempotency_key TEXT,
    context_revision INTEGER NOT NULL,
    context_committed INTEGER NOT NULL,
    error_code TEXT,
    created_at TEXT NOT NULL,
    duration_ms INTEGER NOT NULL,
    values_json TEXT NOT NULL,
    validation_errors_json TEXT NOT NULL,
    ontology_graph_json TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_runtime_run_context ON runtime_run(context_id, created_at);
CREATE INDEX IF NOT EXISTS idx_runtime_run_trace ON runtime_run(trace_id);

CREATE TABLE IF NOT EXISTS execution_snapshot (
    snapshot_id TEXT NOT NULL PRIMARY KEY,
    run_id TEXT NOT NULL,
    phase TEXT NOT NULL,
    context_id TEXT NOT NULL,
    model_id TEXT NOT NULL,
    schema_version INTEGER NOT NULL,
    workflow_version INTEGER NOT NULL,
    state TEXT NOT NULL,
    status TEXT NOT NULL,
    captured_at TEXT NOT NULL,
    values_json TEXT NOT NULL,
    sha256 TEXT NOT NULL,
    FOREIGN KEY (run_id) REFERENCES runtime_run(run_id),
    UNIQUE(run_id, phase)
);

CREATE INDEX IF NOT EXISTS idx_execution_snapshot_run ON execution_snapshot(run_id);

CREATE TABLE IF NOT EXISTS trace (
    trace_id TEXT NOT NULL PRIMARY KEY,
    run_id TEXT NOT NULL UNIQUE,
    started_at TEXT NOT NULL,
    ended_at TEXT NOT NULL,
    duration_ms INTEGER NOT NULL,
    status TEXT NOT NULL,
    sealed INTEGER NOT NULL,
    FOREIGN KEY (run_id) REFERENCES runtime_run(run_id)
);

CREATE TABLE IF NOT EXISTS trace_span (
    span_id TEXT NOT NULL PRIMARY KEY,
    trace_id TEXT NOT NULL,
    name TEXT NOT NULL,
    started_at TEXT NOT NULL,
    ended_at TEXT NOT NULL,
    duration_ms INTEGER NOT NULL,
    status TEXT NOT NULL,
    attributes_json TEXT NOT NULL,
    FOREIGN KEY (trace_id) REFERENCES trace(trace_id)
);

CREATE INDEX IF NOT EXISTS idx_trace_span_trace ON trace_span(trace_id);

CREATE TABLE IF NOT EXISTS audit_event (
    audit_id TEXT NOT NULL PRIMARY KEY,
    action TEXT NOT NULL,
    target_type TEXT NOT NULL,
    target_id TEXT NOT NULL,
    created_at TEXT NOT NULL,
    details TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS idempotency_record (
    scope TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    request_sha256 TEXT NOT NULL,
    run_id TEXT NOT NULL,
    created_at TEXT NOT NULL,
    PRIMARY KEY(scope, idempotency_key),
    FOREIGN KEY (run_id) REFERENCES runtime_run(run_id)
);
