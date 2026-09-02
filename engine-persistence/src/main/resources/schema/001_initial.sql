CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER NOT NULL PRIMARY KEY,
    applied_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS engine_state (
    state_id INTEGER NOT NULL PRIMARY KEY CHECK (state_id = 1),
    payload_json TEXT NOT NULL,
    payload_sha256 TEXT NOT NULL,
    revision INTEGER NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS state_write (
    write_id INTEGER PRIMARY KEY AUTOINCREMENT,
    revision INTEGER NOT NULL,
    payload_sha256 TEXT NOT NULL,
    written_at TEXT NOT NULL
);
