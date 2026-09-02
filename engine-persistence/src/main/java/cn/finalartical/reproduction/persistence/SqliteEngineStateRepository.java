package cn.finalartical.reproduction.persistence;

import cn.finalartical.reproduction.admin.DefaultEngineSeed;
import cn.finalartical.reproduction.admin.EngineState;
import cn.finalartical.reproduction.admin.EngineStateRepository;
import cn.finalartical.reproduction.admin.JsonEngineStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Collections;
import java.util.ConcurrentModificationException;

public final class SqliteEngineStateRepository implements EngineStateRepository {
    private static final int SCHEMA_VERSION = 3;

    private final Path databasePath;
    private final Path legacyJsonPath;
    private final ObjectMapper mapper = new ObjectMapper();

    public SqliteEngineStateRepository(Path databasePath) {
        this(databasePath, null);
    }

    public SqliteEngineStateRepository(Path databasePath, Path legacyJsonPath) {
        if (databasePath == null) {
            throw new IllegalArgumentException("database path must not be null");
        }
        this.databasePath = databasePath;
        this.legacyJsonPath = legacyJsonPath;
    }

    @Override
    public synchronized EngineState load() {
        try (Connection connection = open()) {
            migrate(connection);
            String payload = readPayload(connection);
            if (payload == null) {
                EngineState seed = loadSeed();
                write(connection, seed, 0L);
                return seed;
            }
            EngineState state = mapper.readValue(payload, EngineState.class);
            state.setRevision(currentRevision(connection));
            connection.setAutoCommit(false);
            try {
                synchronizeRuntimeProjection(connection, state);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
            return state;
        } catch (IOException exception) {
            throw new IllegalStateException("cannot decode SQLite engine state: " + databasePath, exception);
        } catch (SQLException exception) {
            throw new IllegalStateException("cannot load SQLite engine state: " + databasePath, exception);
        }
    }

    @Override
    public synchronized void save(EngineState state) {
        if (state == null) {
            throw new IllegalArgumentException("engine state must not be null");
        }
        try (Connection connection = open()) {
            migrate(connection);
            write(connection, state, currentRevision(connection));
        } catch (SQLException exception) {
            throw new IllegalStateException("cannot save SQLite engine state: " + databasePath, exception);
        }
    }

    @Override
    public synchronized void save(EngineState state, long expectedRevision) {
        if (state == null) {
            throw new IllegalArgumentException("engine state must not be null");
        }
        try (Connection connection = open()) {
            migrate(connection);
            write(connection, state, expectedRevision);
        } catch (SQLException exception) {
            throw new IllegalStateException("cannot save SQLite engine state: " + databasePath, exception);
        }
    }

    public Path getDatabasePath() {
        return databasePath;
    }

    public void backupTo(Path target) {
        if (target == null) {
            throw new IllegalArgumentException("backup target must not be null");
        }
        load();
        try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(databasePath, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot backup SQLite engine state: " + target, exception);
        }
    }

    private Connection open() throws SQLException {
        Path parent = databasePath.toAbsolutePath().getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("cannot create database directory: " + parent, exception);
        }
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
        connection.createStatement().execute("PRAGMA foreign_keys = ON");
        return connection;
    }

    private void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL PRIMARY KEY, applied_at TEXT NOT NULL)");
            if (!hasVersion(connection, 1)) {
                for (String sql : readMigration("/schema/001_initial.sql")) {
                    if (!sql.trim().isEmpty()) statement.execute(sql);
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO schema_version(version, applied_at) VALUES (?, ?)")) {
                    insert.setInt(1, 1);
                    insert.setString(2, Instant.now().toString());
                    insert.executeUpdate();
                }
            }
            if (!hasVersion(connection, 2)) {
                for (String sql : readMigration("/schema/002_runtime_domain.sql")) {
                    if (!sql.trim().isEmpty()) statement.execute(sql);
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO schema_version(version, applied_at) VALUES (?, ?)")) {
                    insert.setInt(1, 2);
                    insert.setString(2, Instant.now().toString());
                    insert.executeUpdate();
                }
            }
            if (!hasVersion(connection, 3)) {
                for (String sql : readMigration("/schema/003_retry_and_rollback.sql")) {
                    if (!sql.trim().isEmpty()) statement.execute(sql);
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO schema_version(version, applied_at) VALUES (?, ?)")) {
                    insert.setInt(1, 3);
                    insert.setString(2, Instant.now().toString());
                    insert.executeUpdate();
                }
            }
        }
    }

    private boolean hasVersion(Connection connection, int version) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT version FROM schema_version WHERE version = ?")) {
            query.setInt(1, version);
            try (ResultSet result = query.executeQuery()) {
                return result.next();
            }
        }
    }

    private String[] readMigration(String resourceName) {
        try (InputStream input = SqliteEngineStateRepository.class.getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IllegalStateException("missing SQLite migration resource: " + resourceName);
            }
            String sql = new String(readAll(input), StandardCharsets.UTF_8);
            return sql.split(";\\s*(?:\\r?\\n|$)");
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read SQLite migration", exception);
        }
    }

    private String readPayload(Connection connection) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT payload_json FROM engine_state WHERE state_id = 1")) {
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    private void write(Connection connection, EngineState state, long expectedRevision) throws SQLException {
        long actualRevision = currentRevision(connection);
        if (actualRevision != expectedRevision) {
            throw new ConcurrentModificationException("engine state revision conflict: expected "
                    + expectedRevision + " but was " + actualRevision);
        }
        long revision = expectedRevision + 1L;
        state.setRevision(revision);
        String payload;
        try {
            payload = mapper.writeValueAsString(state);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot encode SQLite engine state", exception);
        }
        String hash = sha256(payload);
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE engine_state SET payload_json = ?, payload_sha256 = ?, revision = ?, updated_at = ? WHERE state_id = 1")) {
                update.setString(1, payload);
                update.setString(2, hash);
                update.setLong(3, revision);
                update.setString(4, state.getUpdatedAt() == null ? Instant.now().toString() : state.getUpdatedAt());
                int updated = update.executeUpdate();
                if (updated == 0) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO engine_state(state_id, payload_json, payload_sha256, revision, updated_at) VALUES (1, ?, ?, ?, ?)")) {
                        insert.setString(1, payload);
                        insert.setString(2, hash);
                        insert.setLong(3, revision);
                        insert.setString(4, state.getUpdatedAt() == null ? Instant.now().toString() : state.getUpdatedAt());
                        insert.executeUpdate();
                    }
                }
            }
            try (PreparedStatement audit = connection.prepareStatement(
                    "INSERT INTO state_write(revision, payload_sha256, written_at) VALUES (?, ?, ?)")) {
                audit.setLong(1, revision);
                audit.setString(2, hash);
                audit.setString(3, Instant.now().toString());
                audit.executeUpdate();
            }
            synchronizeRuntimeProjection(connection, state);
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private long currentRevision(Connection connection) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT revision FROM engine_state WHERE state_id = 1")) {
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }
    }

    private void synchronizeRuntimeProjection(Connection connection, EngineState state) throws SQLException {
        executeDelete(connection, "DELETE FROM trace_span");
        executeDelete(connection, "DELETE FROM trace");
        executeDelete(connection, "DELETE FROM execution_snapshot");
        executeDelete(connection, "DELETE FROM idempotency_record");
        executeDelete(connection, "DELETE FROM runtime_run");
        executeDelete(connection, "DELETE FROM runtime_context");
        executeDelete(connection, "DELETE FROM audit_event");

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO runtime_context(context_id, model_id, schema_version, workflow_version, state, status, revision, last_run_id, last_snapshot_sha256, values_json, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (cn.finalartical.reproduction.admin.RuntimeContextRecord context : state.getContexts()) {
                insert.setString(1, context.getContextId());
                insert.setString(2, context.getModelId());
                insert.setInt(3, context.getSchemaVersion());
                insert.setInt(4, context.getWorkflowVersion());
                insert.setString(5, context.getState());
                insert.setString(6, context.getStatus());
                insert.setLong(7, context.getRevision());
                insert.setString(8, context.getLastRunId());
                insert.setString(9, context.getLastSnapshotSha256());
                insert.setString(10, json(context.getValues()));
                insert.setString(11, context.getUpdatedAt() == null ? Instant.now().toString() : context.getUpdatedAt());
                insert.addBatch();
            }
            insert.executeBatch();
        }

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO runtime_run(run_id, model_id, context_id, engine_version, schema_version, workflow_version, status, data_identity, event, from_state, to_state, trace_id, idempotency_key, context_revision, context_committed, error_code, created_at, duration_ms, values_json, validation_errors_json, ontology_graph_json, input_values_json, retry_of_run_id, attempt) VALUES (" +
                        "?, ?, ?, ?, ?, ?, ?, ?, " +
                        "?, ?, ?, ?, ?, ?, ?, ?, " +
                        "?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (cn.finalartical.reproduction.admin.RuntimeRun run : state.getRuns()) {
                if (run.getId() == null || run.getModelId() == null || run.getContextId() == null
                        || run.getStatus() == null || run.getDataIdentity() == null || run.getEvent() == null
                        || run.getFromState() == null || run.getToState() == null || run.getTraceId() == null
                        || run.getCreatedAt() == null) {
                    continue;
                }
                insert.setString(1, run.getId());
                insert.setString(2, run.getModelId());
                insert.setString(3, run.getContextId());
                insert.setString(4, run.getEngineVersion() == null ? "" : run.getEngineVersion());
                insert.setInt(5, run.getSchemaVersion());
                insert.setInt(6, run.getWorkflowVersion());
                insert.setString(7, run.getStatus());
                insert.setString(8, run.getDataIdentity());
                insert.setString(9, run.getEvent());
                insert.setString(10, run.getFromState());
                insert.setString(11, run.getToState());
                insert.setString(12, run.getTraceId());
                insert.setString(13, run.getIdempotencyKey());
                insert.setLong(14, run.getContextRevision());
                insert.setInt(15, run.isContextCommitted() ? 1 : 0);
                insert.setString(16, run.getErrorCode());
                insert.setString(17, run.getCreatedAt());
                insert.setLong(18, run.getDurationMs());
                insert.setString(19, json(run.getValues()));
                insert.setString(20, json(run.getValidationErrors()));
                insert.setString(21, json(run.getOntologyGraph()));
                insert.setString(22, json(run.getInputValues()));
                insert.setString(23, run.getRetryOfRunId());
                insert.setInt(24, run.getAttempt());
                insert.addBatch();
            }
            insert.executeBatch();
        }

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO execution_snapshot(snapshot_id, run_id, phase, context_id, model_id, schema_version, workflow_version, state, status, captured_at, values_json, sha256) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (cn.finalartical.reproduction.admin.RuntimeRun run : state.getRuns()) {
                if (run.getId() == null || run.getContextId() == null || run.getModelId() == null
                        || run.getFromState() == null || run.getToState() == null || run.getTraceId() == null) continue;
                insertSnapshot(insert, run, run.getBeforeSnapshot());
                insertSnapshot(insert, run, run.getAfterSnapshot());
            }
            insert.executeBatch();
        }

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO trace(trace_id, run_id, started_at, ended_at, duration_ms, status, sealed) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            for (cn.finalartical.reproduction.admin.RuntimeRun run : state.getRuns()) {
                if (run.getId() == null) continue;
                cn.finalartical.reproduction.admin.TraceRecord trace = run.getTrace();
                if (trace == null) continue;
                insert.setString(1, trace.getTraceId());
                insert.setString(2, trace.getRunId());
                insert.setString(3, trace.getStartedAt());
                insert.setString(4, trace.getEndedAt());
                insert.setLong(5, trace.getDurationMs());
                insert.setString(6, trace.getStatus());
                insert.setInt(7, trace.isSealed() ? 1 : 0);
                insert.addBatch();
            }
            insert.executeBatch();
        }

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO trace_span(span_id, trace_id, name, started_at, ended_at, duration_ms, status, attributes_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (cn.finalartical.reproduction.admin.RuntimeRun run : state.getRuns()) {
                if (run.getId() == null) continue;
                cn.finalartical.reproduction.admin.TraceRecord trace = run.getTrace();
                if (trace == null) continue;
                for (cn.finalartical.reproduction.admin.TraceSpanRecord span : trace.getSpans()) {
                    insert.setString(1, span.getSpanId());
                    insert.setString(2, span.getTraceId());
                    insert.setString(3, span.getName());
                    insert.setString(4, span.getStartedAt());
                    insert.setString(5, span.getEndedAt());
                    insert.setLong(6, span.getDurationMs());
                    insert.setString(7, span.getStatus());
                    insert.setString(8, json(span.getAttributes()));
                    insert.addBatch();
                }
            }
            insert.executeBatch();
        }

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO audit_event(audit_id, action, target_type, target_id, created_at, details) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (cn.finalartical.reproduction.admin.AuditEventRecord event : state.getAuditEvents()) {
                insert.setString(1, event.getId());
                insert.setString(2, event.getAction());
                insert.setString(3, event.getTargetType());
                insert.setString(4, event.getTargetId());
                insert.setString(5, event.getCreatedAt());
                insert.setString(6, event.getDetails());
                insert.addBatch();
            }
            insert.executeBatch();
        }

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO idempotency_record(scope, idempotency_key, request_sha256, run_id, created_at) VALUES (?, ?, ?, ?, ?)")) {
            for (cn.finalartical.reproduction.admin.IdempotencyRecord record : state.getIdempotencyRecords()) {
                if (record.getScope() == null || record.getKey() == null || record.getRequestSha256() == null
                        || record.getRunId() == null || record.getCreatedAt() == null || !hasRun(state, record.getRunId())) continue;
                insert.setString(1, record.getScope());
                insert.setString(2, record.getKey());
                insert.setString(3, record.getRequestSha256());
                insert.setString(4, record.getRunId());
                insert.setString(5, record.getCreatedAt());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void insertSnapshot(PreparedStatement insert, cn.finalartical.reproduction.admin.RuntimeRun run,
                                cn.finalartical.reproduction.admin.ExecutionSnapshotRecord snapshot) throws SQLException {
        if (snapshot == null) return;
        insert.setString(1, run.getId() + ":" + snapshot.getPhase().toLowerCase());
        insert.setString(2, run.getId());
        insert.setString(3, snapshot.getPhase());
        insert.setString(4, snapshot.getContextId());
        insert.setString(5, snapshot.getModelId());
        insert.setInt(6, snapshot.getSchemaVersion());
        insert.setInt(7, snapshot.getWorkflowVersion());
        insert.setString(8, snapshot.getState());
        insert.setString(9, snapshot.getStatus());
        insert.setString(10, snapshot.getCapturedAt());
        insert.setString(11, json(snapshot.getValues()));
        insert.setString(12, snapshot.getSha256());
        insert.addBatch();
    }

    private boolean hasRun(EngineState state, String runId) {
        for (cn.finalartical.reproduction.admin.RuntimeRun run : state.getRuns()) {
            if (runId.equals(run.getId()) && run.getContextId() != null) return true;
        }
        return false;
    }

    private void executeDelete(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private String json(Object value) throws SQLException {
        try {
            return mapper.writeValueAsString(value == null ? Collections.emptyMap() : value);
        } catch (IOException exception) {
            throw new SQLException("cannot encode runtime projection", exception);
        }
    }

    private EngineState loadSeed() {
        if (legacyJsonPath != null && Files.exists(legacyJsonPath)) {
            return new JsonEngineStateRepository(legacyJsonPath).load();
        }
        return DefaultEngineSeed.create();
    }

    private static byte[] readAll(InputStream input) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length;
        while ((length = input.read(buffer)) != -1) {
            output.write(buffer, 0, length);
        }
        return output.toByteArray();
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
