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
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SqliteEngineStateRepository implements EngineStateRepository {
    private static final int SCHEMA_VERSION = 5;

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
                validateNormalizedProjection(connection);
                loadConfigurationProjection(connection, state);
                loadRuntimeProjection(connection, state);
                synchronizeConfigurationProjection(connection, state);
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
            if (!hasVersion(connection, 4)) {
                for (String sql : readMigration("/schema/004_configuration_domain.sql")) {
                    if (!sql.trim().isEmpty()) statement.execute(sql);
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO schema_version(version, applied_at) VALUES (?, ?)")) {
                    insert.setInt(1, 4);
                    insert.setString(2, Instant.now().toString());
                    insert.executeUpdate();
                }
            }
            if (!hasVersion(connection, 5)) {
                for (String sql : readMigration("/schema/005_schema_migration.sql")) {
                    if (!sql.trim().isEmpty()) statement.execute(sql);
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO schema_version(version, applied_at) VALUES (?, ?)")) {
                    insert.setInt(1, 5);
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
            synchronizeConfigurationProjection(connection, state);
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

    private void synchronizeConfigurationProjection(Connection connection, EngineState state) throws SQLException {
        String projectionTime = state.getUpdatedAt() == null ? Instant.now().toString() : state.getUpdatedAt();
        executeDelete(connection, "DELETE FROM schema_field");
        executeDelete(connection, "DELETE FROM schema_migration");
        executeDelete(connection, "DELETE FROM schema_definition");
        executeDelete(connection, "DELETE FROM workflow_transition");
        executeDelete(connection, "DELETE FROM workflow_definition");
        executeDelete(connection, "DELETE FROM ontology_relation");
        executeDelete(connection, "DELETE FROM ontology_attribute");
        executeDelete(connection, "DELETE FROM ontology_type");
        executeDelete(connection, "DELETE FROM service_registration");
        executeDelete(connection, "DELETE FROM engine_model");

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO engine_model(model_id, name, description, schema_version, workflow_version, initial_state, unknown_field_policy, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (cn.finalartical.reproduction.admin.EngineModel model : state.getModels()) {
                insert.setString(1, model.getId());
                insert.setString(2, model.getName());
                insert.setString(3, model.getDescription());
                insert.setInt(4, model.getSchemaVersion());
                insert.setInt(5, model.getWorkflowVersion());
                insert.setString(6, model.getInitialState());
                insert.setString(7, model.getUnknownFieldPolicy());
                insert.setString(8, model.getUpdatedAt() == null ? Instant.now().toString() : model.getUpdatedAt());
                insert.addBatch();
            }
            insert.executeBatch();
        }

        try (PreparedStatement definitionInsert = connection.prepareStatement(
                "INSERT INTO schema_definition(model_id, schema_version, published_at) VALUES (?, ?, ?)" );
             PreparedStatement fieldInsert = connection.prepareStatement(
                     "INSERT INTO schema_field(model_id, schema_version, field_name, field_type, required, field_version, default_value_json, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (cn.finalartical.reproduction.admin.EngineModel model : state.getModels()) {
                List<cn.finalartical.reproduction.admin.SchemaVersionRecord> versions = model.getSchemaVersions();
                if (versions.isEmpty()) {
                    versions = Collections.singletonList(new cn.finalartical.reproduction.admin.SchemaVersionRecord(
                            model.getSchemaVersion(), model.getUpdatedAt() == null ? Instant.now().toString() : model.getUpdatedAt(),
                            model.getFields()));
                }
                for (cn.finalartical.reproduction.admin.SchemaVersionRecord version : versions) {
                    definitionInsert.setString(1, model.getId());
                    definitionInsert.setInt(2, version.getVersion());
                    definitionInsert.setString(3, version.getPublishedAt() == null ? Instant.now().toString() : version.getPublishedAt());
                    definitionInsert.addBatch();
                    for (cn.finalartical.reproduction.admin.EngineField field : version.getFields()) {
                        fieldInsert.setString(1, model.getId());
                        fieldInsert.setInt(2, version.getVersion());
                        fieldInsert.setString(3, field.getName());
                        fieldInsert.setString(4, field.getType());
                        fieldInsert.setInt(5, field.isRequired() ? 1 : 0);
                        fieldInsert.setInt(6, field.getVersion());
                        fieldInsert.setString(7, field.getDefaultValue() == null ? null : json(field.getDefaultValue()));
                        fieldInsert.setString(8, projectionTime);
                        fieldInsert.addBatch();
                    }
                }
            }
            definitionInsert.executeBatch();
            fieldInsert.executeBatch();
        }

        try (PreparedStatement migrationInsert = connection.prepareStatement(
                "INSERT INTO schema_migration(model_id, from_version, to_version, source_field, target_field, created_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (cn.finalartical.reproduction.admin.EngineModel model : state.getModels()) {
                for (cn.finalartical.reproduction.admin.SchemaMigrationRecord migration : model.getSchemaMigrations()) {
                    migrationInsert.setString(1, model.getId());
                    migrationInsert.setInt(2, migration.getFromVersion());
                    migrationInsert.setInt(3, migration.getToVersion());
                    migrationInsert.setString(4, migration.getSourceField());
                    migrationInsert.setString(5, migration.getTargetField());
                    migrationInsert.setString(6, projectionTime);
                    migrationInsert.addBatch();
                }
            }
            migrationInsert.executeBatch();
        }

        try (PreparedStatement definitionInsert = connection.prepareStatement(
                "INSERT INTO workflow_definition(model_id, workflow_version, initial_state, published_at) VALUES (?, ?, ?, ?)" );
             PreparedStatement transitionInsert = connection.prepareStatement(
                     "INSERT INTO workflow_transition(model_id, workflow_version, from_state, event, to_state, updated_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (cn.finalartical.reproduction.admin.EngineModel model : state.getModels()) {
                List<cn.finalartical.reproduction.admin.WorkflowVersionRecord> versions = model.getWorkflowVersions();
                if (versions.isEmpty()) {
                    versions = Collections.singletonList(new cn.finalartical.reproduction.admin.WorkflowVersionRecord(
                            model.getWorkflowVersion(), model.getUpdatedAt() == null ? Instant.now().toString() : model.getUpdatedAt(),
                            model.getInitialState(), model.getTransitions()));
                }
                for (cn.finalartical.reproduction.admin.WorkflowVersionRecord version : versions) {
                    definitionInsert.setString(1, model.getId());
                    definitionInsert.setInt(2, version.getVersion());
                    definitionInsert.setString(3, version.getInitialState());
                    definitionInsert.setString(4, version.getPublishedAt() == null ? Instant.now().toString() : version.getPublishedAt());
                    definitionInsert.addBatch();
                    for (cn.finalartical.reproduction.admin.EngineTransition transition : version.getTransitions()) {
                        transitionInsert.setString(1, model.getId());
                        transitionInsert.setInt(2, version.getVersion());
                        transitionInsert.setString(3, transition.getFromState());
                        transitionInsert.setString(4, transition.getEvent());
                        transitionInsert.setString(5, transition.getToState());
                        transitionInsert.setString(6, projectionTime);
                        transitionInsert.addBatch();
                    }
                }
            }
            definitionInsert.executeBatch();
            transitionInsert.executeBatch();
        }

        try (PreparedStatement typeInsert = connection.prepareStatement(
                "INSERT INTO ontology_type(ontology_type_id, label, description, type_version, created_at) VALUES (?, ?, ?, ?, ?)" );
             PreparedStatement attributeInsert = connection.prepareStatement(
                     "INSERT INTO ontology_attribute(ontology_type_id, attribute_name, attribute_kind, type_version, created_at) VALUES (?, ?, ?, ?, ?)" );
             PreparedStatement relationInsert = connection.prepareStatement(
                     "INSERT INTO ontology_relation(ontology_type_id, relation_name, target_type, cardinality, type_version, created_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (cn.finalartical.reproduction.admin.OntologyTypeConfig type : state.getOntologyTypes()) {
                typeInsert.setString(1, type.getId());
                typeInsert.setString(2, type.getLabel());
                typeInsert.setString(3, type.getDescription());
                typeInsert.setInt(4, 1);
                typeInsert.setString(5, projectionTime);
                typeInsert.addBatch();
                for (String attribute : type.getFixedAttributes()) {
                    attributeInsert.setString(1, type.getId());
                    attributeInsert.setString(2, attribute);
                    attributeInsert.setString(3, "FIXED");
                    attributeInsert.setInt(4, 1);
                    attributeInsert.setString(5, projectionTime);
                    attributeInsert.addBatch();
                }
                for (String attribute : type.getDynamicAttributes()) {
                    attributeInsert.setString(1, type.getId());
                    attributeInsert.setString(2, attribute);
                    attributeInsert.setString(3, "DYNAMIC");
                    attributeInsert.setInt(4, 1);
                    attributeInsert.setString(5, projectionTime);
                    attributeInsert.addBatch();
                }
                for (cn.finalartical.reproduction.admin.OntologyRelationConfig relation : type.getRelations()) {
                    relationInsert.setString(1, type.getId());
                    relationInsert.setString(2, relation.getName());
                    relationInsert.setString(3, relation.getTargetType());
                    relationInsert.setString(4, relation.getCardinality());
                    relationInsert.setInt(5, 1);
                    relationInsert.setString(6, projectionTime);
                    relationInsert.addBatch();
                }
            }
            typeInsert.executeBatch();
            attributeInsert.executeBatch();
            relationInsert.executeBatch();
        }

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO service_registration(service_id, name, provider, status, endpoint, version, registered_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            for (cn.finalartical.reproduction.admin.ServiceRegistration service : state.getServices()) {
                insert.setString(1, service.getId());
                insert.setString(2, service.getName());
                insert.setString(3, service.getProvider());
                insert.setString(4, service.getStatus());
                insert.setString(5, service.getEndpoint());
                insert.setString(6, service.getVersion());
                insert.setString(7, projectionTime);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    /**
     * Rehydrates configuration from normalized tables before the compatibility
     * aggregate is projected again.  The aggregate remains a migration/backup
     * envelope for runtime history, but configuration reads no longer depend on
     * its embedded JSON arrays once the normalized configuration projection has data.
     */
    private void loadConfigurationProjection(Connection connection, EngineState state) throws SQLException {
        if (!hasConfigurationProjection(connection)) {
            return;
        }

        Map<String, cn.finalartical.reproduction.admin.EngineModel> models = new LinkedHashMap<String, cn.finalartical.reproduction.admin.EngineModel>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT model_id, name, description, schema_version, workflow_version, initial_state, unknown_field_policy, updated_at "
                        + "FROM engine_model ORDER BY model_id")) {
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    cn.finalartical.reproduction.admin.EngineModel model = new cn.finalartical.reproduction.admin.EngineModel(
                            result.getString("model_id"), result.getString("name"), result.getString("description"),
                            result.getInt("schema_version"), result.getString("initial_state"));
                    model.setWorkflowVersion(result.getInt("workflow_version"));
                    model.setUnknownFieldPolicy(result.getString("unknown_field_policy"));
                    model.setUpdatedAt(result.getString("updated_at"));
                    models.put(model.getId(), model);
                }
            }
        }

        Map<String, Map<Integer, cn.finalartical.reproduction.admin.SchemaVersionRecord>> schemaVersions =
                new LinkedHashMap<String, Map<Integer, cn.finalartical.reproduction.admin.SchemaVersionRecord>>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT model_id, schema_version, published_at FROM schema_definition ORDER BY model_id, schema_version")) {
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    String modelId = result.getString("model_id");
                    Map<Integer, cn.finalartical.reproduction.admin.SchemaVersionRecord> versions = schemaVersions.get(modelId);
                    if (versions == null) {
                        versions = new LinkedHashMap<Integer, cn.finalartical.reproduction.admin.SchemaVersionRecord>();
                        schemaVersions.put(modelId, versions);
                    }
                    int version = result.getInt("schema_version");
                    versions.put(version, new cn.finalartical.reproduction.admin.SchemaVersionRecord(
                            version, result.getString("published_at"), new ArrayList<cn.finalartical.reproduction.admin.EngineField>()));
                }
            }
        }
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT model_id, schema_version, field_name, field_type, required, field_version, default_value_json "
                        + "FROM schema_field ORDER BY model_id, schema_version, field_name")) {
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    Map<Integer, cn.finalartical.reproduction.admin.SchemaVersionRecord> versions = schemaVersions.get(result.getString("model_id"));
                    if (versions == null || !versions.containsKey(result.getInt("schema_version"))) {
                        throw new SQLException("schema_field has no parent schema_definition: " + result.getString("field_name"));
                    }
                    versions.get(result.getInt("schema_version")).getFields().add(new cn.finalartical.reproduction.admin.EngineField(
                            result.getString("field_name"), result.getString("field_type"), result.getInt("required") != 0,
                            result.getInt("field_version"), readJsonValue(result.getString("default_value_json"))));
                }
            }
        }
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT model_id, from_version, to_version, source_field, target_field "
                        + "FROM schema_migration ORDER BY model_id, from_version, to_version, source_field")) {
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    String modelId = result.getString("model_id");
                    Map<Integer, cn.finalartical.reproduction.admin.SchemaVersionRecord> versions = schemaVersions.get(modelId);
                    if (versions == null || !versions.containsKey(result.getInt("from_version"))
                            || !versions.containsKey(result.getInt("to_version"))) {
                        throw new SQLException("schema_migration has no parent schema version: " + modelId);
                    }
                    cn.finalartical.reproduction.admin.EngineModel model = models.get(modelId);
                    if (model == null) {
                        throw new SQLException("schema_migration has no parent engine model: " + modelId);
                    }
                    model.getSchemaMigrations().add(new cn.finalartical.reproduction.admin.SchemaMigrationRecord(
                            result.getInt("from_version"), result.getInt("to_version"),
                            result.getString("source_field"), result.getString("target_field")));
                }
            }
        }
        for (Map.Entry<String, cn.finalartical.reproduction.admin.EngineModel> entry : models.entrySet()) {
            Map<Integer, cn.finalartical.reproduction.admin.SchemaVersionRecord> versions = schemaVersions.get(entry.getKey());
            List<cn.finalartical.reproduction.admin.SchemaVersionRecord> ordered = new ArrayList<cn.finalartical.reproduction.admin.SchemaVersionRecord>();
            if (versions != null) {
                ordered.addAll(versions.values());
            }
            entry.getValue().setSchemaVersions(ordered);
            cn.finalartical.reproduction.admin.SchemaVersionRecord current = versions == null ? null
                    : versions.get(entry.getValue().getSchemaVersion());
            entry.getValue().setFields(current == null ? Collections.<cn.finalartical.reproduction.admin.EngineField>emptyList()
                    : current.getFields());
        }

        Map<String, Map<Integer, cn.finalartical.reproduction.admin.WorkflowVersionRecord>> workflowVersions =
                new LinkedHashMap<String, Map<Integer, cn.finalartical.reproduction.admin.WorkflowVersionRecord>>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT model_id, workflow_version, initial_state, published_at FROM workflow_definition ORDER BY model_id, workflow_version")) {
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    String modelId = result.getString("model_id");
                    Map<Integer, cn.finalartical.reproduction.admin.WorkflowVersionRecord> versions = workflowVersions.get(modelId);
                    if (versions == null) {
                        versions = new LinkedHashMap<Integer, cn.finalartical.reproduction.admin.WorkflowVersionRecord>();
                        workflowVersions.put(modelId, versions);
                    }
                    int version = result.getInt("workflow_version");
                    versions.put(version, new cn.finalartical.reproduction.admin.WorkflowVersionRecord(
                            version, result.getString("published_at"), result.getString("initial_state"),
                            new ArrayList<cn.finalartical.reproduction.admin.EngineTransition>()));
                }
            }
        }
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT model_id, workflow_version, from_state, event, to_state FROM workflow_transition "
                        + "ORDER BY model_id, workflow_version, from_state, event")) {
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    Map<Integer, cn.finalartical.reproduction.admin.WorkflowVersionRecord> versions = workflowVersions.get(result.getString("model_id"));
                    if (versions == null || !versions.containsKey(result.getInt("workflow_version"))) {
                        throw new SQLException("workflow_transition has no parent workflow_definition: " + result.getString("event"));
                    }
                    versions.get(result.getInt("workflow_version")).getTransitions().add(new cn.finalartical.reproduction.admin.EngineTransition(
                            result.getString("from_state"), result.getString("event"), result.getString("to_state")));
                }
            }
        }
        for (Map.Entry<String, cn.finalartical.reproduction.admin.EngineModel> entry : models.entrySet()) {
            Map<Integer, cn.finalartical.reproduction.admin.WorkflowVersionRecord> versions = workflowVersions.get(entry.getKey());
            List<cn.finalartical.reproduction.admin.WorkflowVersionRecord> ordered = new ArrayList<cn.finalartical.reproduction.admin.WorkflowVersionRecord>();
            if (versions != null) {
                ordered.addAll(versions.values());
            }
            entry.getValue().setWorkflowVersions(ordered);
            cn.finalartical.reproduction.admin.WorkflowVersionRecord current = versions == null ? null
                    : versions.get(entry.getValue().getWorkflowVersion());
            List<cn.finalartical.reproduction.admin.EngineTransition> transitions = current == null
                    ? Collections.<cn.finalartical.reproduction.admin.EngineTransition>emptyList() : current.getTransitions();
            entry.getValue().setTransitions(transitions);
            List<String> states = new ArrayList<String>();
            addState(states, entry.getValue().getInitialState());
            for (cn.finalartical.reproduction.admin.EngineTransition transition : transitions) {
                addState(states, transition.getFromState());
                addState(states, transition.getToState());
            }
            entry.getValue().setStates(states);
        }

        Map<String, cn.finalartical.reproduction.admin.OntologyTypeConfig> ontologyTypes =
                new LinkedHashMap<String, cn.finalartical.reproduction.admin.OntologyTypeConfig>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT ontology_type_id, label, description FROM ontology_type ORDER BY ontology_type_id")) {
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    ontologyTypes.put(result.getString("ontology_type_id"),
                            new cn.finalartical.reproduction.admin.OntologyTypeConfig(result.getString("ontology_type_id"),
                                    result.getString("label"), result.getString("description")));
                }
            }
        }
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT ontology_type_id, attribute_name, attribute_kind FROM ontology_attribute "
                        + "ORDER BY ontology_type_id, attribute_name")) {
            try (ResultSet result = query.executeQuery()) {
                cn.finalartical.reproduction.admin.OntologyTypeConfig type;
                while (result.next()) {
                    type = ontologyTypes.get(result.getString("ontology_type_id"));
                    if (type == null) {
                        throw new SQLException("ontology_attribute has no parent ontology_type: " + result.getString("attribute_name"));
                    }
                    if ("FIXED".equalsIgnoreCase(result.getString("attribute_kind"))) {
                        type.getFixedAttributes().add(result.getString("attribute_name"));
                    } else {
                        type.getDynamicAttributes().add(result.getString("attribute_name"));
                    }
                }
            }
        }
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT ontology_type_id, relation_name, target_type, cardinality FROM ontology_relation "
                        + "ORDER BY ontology_type_id, relation_name")) {
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    cn.finalartical.reproduction.admin.OntologyTypeConfig type = ontologyTypes.get(result.getString("ontology_type_id"));
                    if (type == null) {
                        throw new SQLException("ontology_relation has no parent ontology_type: " + result.getString("relation_name"));
                    }
                    type.getRelations().add(new cn.finalartical.reproduction.admin.OntologyRelationConfig(
                            result.getString("relation_name"), result.getString("target_type"), result.getString("cardinality")));
                }
            }
        }

        List<cn.finalartical.reproduction.admin.ServiceRegistration> services = new ArrayList<cn.finalartical.reproduction.admin.ServiceRegistration>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT service_id, name, provider, status, endpoint, version FROM service_registration ORDER BY service_id")) {
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    services.add(new cn.finalartical.reproduction.admin.ServiceRegistration(result.getString("service_id"),
                            result.getString("name"), result.getString("provider"), result.getString("status"),
                            result.getString("endpoint"), result.getString("version")));
                }
            }
        }
        state.setModels(new ArrayList<cn.finalartical.reproduction.admin.EngineModel>(models.values()));
        state.setOntologyTypes(new ArrayList<cn.finalartical.reproduction.admin.OntologyTypeConfig>(ontologyTypes.values()));
        state.setServices(services);
    }

    private boolean hasConfigurationProjection(Connection connection) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT count(*) FROM engine_model")) {
            try (ResultSet result = query.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }

    private void validateNormalizedProjection(Connection connection) throws SQLException {
        boolean hasConfigurationProjection = rowCount(connection, "engine_model") > 0;
        if (hasConfigurationProjection) {
            assertNoRows(connection,
                    "SELECT m.model_id FROM engine_model m "
                            + "LEFT JOIN schema_definition s ON s.model_id = m.model_id AND s.schema_version = m.schema_version "
                            + "WHERE s.model_id IS NULL",
                    "engine_model points to a missing current schema definition");
            assertNoRows(connection,
                    "SELECT m.model_id FROM engine_model m "
                            + "LEFT JOIN workflow_definition w ON w.model_id = m.model_id AND w.workflow_version = m.workflow_version "
                            + "WHERE w.model_id IS NULL",
                    "engine_model points to a missing current workflow definition");
            assertNoRows(connection,
                "SELECT r.ontology_type_id || ':' || r.relation_name FROM ontology_relation r "
                            + "LEFT JOIN ontology_type target ON target.ontology_type_id = r.target_type "
                            + "OR lower(target.label) = lower(r.target_type) "
                            + "WHERE target.ontology_type_id IS NULL",
                    "ontology_relation points to a missing target ontology type");
        }
        if (hasConfigurationProjection && rowCount(connection, "runtime_context") > 0) {
            assertNoRows(connection,
                    "SELECT c.context_id FROM runtime_context c "
                            + "LEFT JOIN engine_model m ON m.model_id = c.model_id "
                            + "WHERE m.model_id IS NULL",
                    "runtime_context points to a missing engine model");
        }
        if (hasConfigurationProjection && rowCount(connection, "runtime_run") > 0) {
            assertNoRows(connection,
                    "SELECT r.run_id FROM runtime_run r "
                            + "LEFT JOIN engine_model m ON m.model_id = r.model_id "
                            + "WHERE m.model_id IS NULL",
                    "runtime_run points to a missing engine model");
            assertNoRows(connection,
                    "SELECT r.run_id FROM runtime_run r "
                            + "LEFT JOIN runtime_context c ON c.context_id = r.context_id "
                            + "WHERE c.context_id IS NULL",
                    "runtime_run points to a missing runtime context");
            assertNoRows(connection,
                    "SELECT r.run_id FROM runtime_run r "
                            + "JOIN runtime_context c ON c.context_id = r.context_id "
                            + "WHERE c.model_id <> r.model_id",
                    "runtime_run model does not match its runtime context");
        }
        assertNoRows(connection,
                "SELECT snapshot_id FROM execution_snapshot s "
                        + "LEFT JOIN runtime_run r ON r.run_id = s.run_id "
                        + "WHERE r.run_id IS NULL",
                "execution_snapshot points to a missing runtime run");
        assertNoRows(connection,
                "SELECT t.trace_id FROM trace t "
                        + "LEFT JOIN runtime_run r ON r.run_id = t.run_id "
                        + "WHERE r.run_id IS NULL",
                "trace points to a missing runtime run");
        assertNoRows(connection,
                "SELECT s.span_id FROM trace_span s "
                        + "LEFT JOIN trace t ON t.trace_id = s.trace_id "
                        + "WHERE t.trace_id IS NULL",
                "trace_span points to a missing trace");
        assertNoRows(connection,
                "SELECT i.scope || ':' || i.idempotency_key FROM idempotency_record i "
                        + "LEFT JOIN runtime_run r ON r.run_id = i.run_id "
                        + "WHERE r.run_id IS NULL",
                "idempotency_record points to a missing runtime run");
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA foreign_key_check")) {
            if (result.next()) {
                throw new SQLException("SQLite normalized projection foreign key check failed: "
                        + result.getString(1) + ":" + result.getString(2));
            }
        }
    }

    private void assertNoRows(Connection connection, String sql, String message) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            if (result.next()) {
                throw new SQLException(message + ": " + result.getString(1));
            }
        }
    }

    private void loadRuntimeProjection(Connection connection, EngineState state) throws SQLException {
        if (rowCount(connection, "runtime_context") > 0) {
            List<cn.finalartical.reproduction.admin.RuntimeContextRecord> contexts = new ArrayList<cn.finalartical.reproduction.admin.RuntimeContextRecord>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT context_id, model_id, schema_version, workflow_version, state, status, revision, "
                            + "last_run_id, last_snapshot_sha256, values_json, updated_at FROM runtime_context ORDER BY updated_at DESC")) {
                try (ResultSet result = query.executeQuery()) {
                    while (result.next()) {
                        cn.finalartical.reproduction.admin.RuntimeContextRecord context =
                                new cn.finalartical.reproduction.admin.RuntimeContextRecord(result.getString("context_id"),
                                        result.getString("model_id"), result.getInt("schema_version"),
                                        result.getInt("workflow_version"), result.getString("state"),
                                        result.getString("status"), result.getLong("revision"), result.getString("updated_at"));
                        context.setLastRunId(result.getString("last_run_id"));
                        context.setLastSnapshotSha256(result.getString("last_snapshot_sha256"));
                        context.setValues(readMapValue(result.getString("values_json")));
                        contexts.add(context);
                    }
                }
            }
            state.setContexts(contexts);
        }

        Map<String, cn.finalartical.reproduction.admin.RuntimeRun> runs = new LinkedHashMap<String, cn.finalartical.reproduction.admin.RuntimeRun>();
        if (rowCount(connection, "runtime_run") > 0) {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT run_id, model_id, context_id, engine_version, schema_version, workflow_version, status, "
                            + "data_identity, event, from_state, to_state, trace_id, idempotency_key, context_revision, "
                            + "context_committed, error_code, created_at, duration_ms, values_json, validation_errors_json, "
                            + "ontology_graph_json, input_values_json, retry_of_run_id, attempt FROM runtime_run ORDER BY created_at DESC")) {
                try (ResultSet result = query.executeQuery()) {
                    while (result.next()) {
                        cn.finalartical.reproduction.admin.RuntimeRun run = new cn.finalartical.reproduction.admin.RuntimeRun();
                        run.setId(result.getString("run_id"));
                        run.setModelId(result.getString("model_id"));
                        run.setContextId(result.getString("context_id"));
                        run.setEngineVersion(result.getString("engine_version"));
                        run.setSchemaVersion(result.getInt("schema_version"));
                        run.setWorkflowVersion(result.getInt("workflow_version"));
                        run.setStatus(result.getString("status"));
                        run.setDataIdentity(result.getString("data_identity"));
                        run.setEvent(result.getString("event"));
                        run.setFromState(result.getString("from_state"));
                        run.setToState(result.getString("to_state"));
                        run.setTraceId(result.getString("trace_id"));
                        run.setIdempotencyKey(result.getString("idempotency_key"));
                        run.setContextRevision(result.getLong("context_revision"));
                        run.setContextCommitted(result.getInt("context_committed") != 0);
                        run.setErrorCode(result.getString("error_code"));
                        run.setCreatedAt(result.getString("created_at"));
                        run.setDurationMs(result.getLong("duration_ms"));
                        run.setValues(readMapValue(result.getString("values_json")));
                        run.setValidationErrors(readStringListValue(result.getString("validation_errors_json")));
                        run.setOntologyGraph(readMapValue(result.getString("ontology_graph_json")));
                        run.setInputValues(readMapValue(result.getString("input_values_json")));
                        run.setRetryOfRunId(result.getString("retry_of_run_id"));
                        run.setAttempt(result.getInt("attempt"));
                        runs.put(run.getId(), run);
                    }
                }
            }
            state.setRuns(new ArrayList<cn.finalartical.reproduction.admin.RuntimeRun>(runs.values()));
        }

        if (rowCount(connection, "execution_snapshot") > 0 && !runs.isEmpty()) {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT run_id, phase, context_id, model_id, schema_version, workflow_version, state, status, "
                            + "captured_at, values_json, sha256 FROM execution_snapshot ORDER BY captured_at")) {
                try (ResultSet result = query.executeQuery()) {
                    while (result.next()) {
                        cn.finalartical.reproduction.admin.RuntimeRun run = runs.get(result.getString("run_id"));
                        if (run == null) {
                            continue;
                        }
                        cn.finalartical.reproduction.admin.ExecutionSnapshotRecord snapshot = new cn.finalartical.reproduction.admin.ExecutionSnapshotRecord();
                        snapshot.setPhase(result.getString("phase"));
                        snapshot.setContextId(result.getString("context_id"));
                        snapshot.setModelId(result.getString("model_id"));
                        snapshot.setSchemaVersion(result.getInt("schema_version"));
                        snapshot.setWorkflowVersion(result.getInt("workflow_version"));
                        snapshot.setState(result.getString("state"));
                        snapshot.setStatus(result.getString("status"));
                        snapshot.setCapturedAt(result.getString("captured_at"));
                        snapshot.setValues(readMapValue(result.getString("values_json")));
                        snapshot.setSha256(result.getString("sha256"));
                        if ("BEFORE".equalsIgnoreCase(snapshot.getPhase())) {
                            run.setBeforeSnapshot(snapshot);
                        } else if ("AFTER".equalsIgnoreCase(snapshot.getPhase())) {
                            run.setAfterSnapshot(snapshot);
                        }
                    }
                }
            }
        }

        Map<String, cn.finalartical.reproduction.admin.TraceRecord> traces = new LinkedHashMap<String, cn.finalartical.reproduction.admin.TraceRecord>();
        if (rowCount(connection, "trace") > 0 && !runs.isEmpty()) {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT trace_id, run_id, started_at, ended_at, duration_ms, status, sealed FROM trace ORDER BY started_at DESC")) {
                try (ResultSet result = query.executeQuery()) {
                    while (result.next()) {
                        cn.finalartical.reproduction.admin.RuntimeRun run = runs.get(result.getString("run_id"));
                        if (run == null) {
                            continue;
                        }
                        cn.finalartical.reproduction.admin.TraceRecord trace = new cn.finalartical.reproduction.admin.TraceRecord();
                        trace.setTraceId(result.getString("trace_id"));
                        trace.setRunId(result.getString("run_id"));
                        trace.setStartedAt(result.getString("started_at"));
                        trace.setEndedAt(result.getString("ended_at"));
                        trace.setDurationMs(result.getLong("duration_ms"));
                        trace.setStatus(result.getString("status"));
                        trace.setSealed(result.getInt("sealed") != 0);
                        run.setTrace(trace);
                        traces.put(trace.getTraceId(), trace);
                    }
                }
            }
        }
        if (rowCount(connection, "trace_span") > 0 && !traces.isEmpty()) {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT span_id, trace_id, name, started_at, ended_at, duration_ms, status, attributes_json "
                            + "FROM trace_span ORDER BY started_at")) {
                try (ResultSet result = query.executeQuery()) {
                    while (result.next()) {
                        cn.finalartical.reproduction.admin.TraceRecord trace = traces.get(result.getString("trace_id"));
                        if (trace == null) {
                            continue;
                        }
                        trace.getSpans().add(new cn.finalartical.reproduction.admin.TraceSpanRecord(
                                result.getString("span_id"), result.getString("trace_id"), result.getString("name"),
                                result.getString("started_at"), result.getString("ended_at"), result.getLong("duration_ms"),
                                result.getString("status"), readStringMapValue(result.getString("attributes_json"))));
                    }
                }
            }
        }

        if (rowCount(connection, "audit_event") > 0) {
            List<cn.finalartical.reproduction.admin.AuditEventRecord> events = new ArrayList<cn.finalartical.reproduction.admin.AuditEventRecord>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT audit_id, action, target_type, target_id, created_at, details FROM audit_event ORDER BY created_at DESC")) {
                try (ResultSet result = query.executeQuery()) {
                    while (result.next()) {
                        events.add(new cn.finalartical.reproduction.admin.AuditEventRecord(result.getString("audit_id"),
                                result.getString("action"), result.getString("target_type"), result.getString("target_id"),
                                result.getString("created_at"), result.getString("details")));
                    }
                }
            }
            state.setAuditEvents(events);
        }
        if (rowCount(connection, "idempotency_record") > 0) {
            List<cn.finalartical.reproduction.admin.IdempotencyRecord> records = new ArrayList<cn.finalartical.reproduction.admin.IdempotencyRecord>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT scope, idempotency_key, request_sha256, run_id, created_at FROM idempotency_record ORDER BY created_at DESC")) {
                try (ResultSet result = query.executeQuery()) {
                    while (result.next()) {
                        records.add(new cn.finalartical.reproduction.admin.IdempotencyRecord(result.getString("scope"),
                                result.getString("idempotency_key"), result.getString("request_sha256"),
                                result.getString("run_id"), result.getString("created_at")));
                    }
                }
            }
            state.setIdempotencyRecords(records);
        }
    }

    private int rowCount(Connection connection, String table) throws SQLException {
        if (!table.matches("[a-z_]+")) {
            throw new IllegalArgumentException("invalid projection table: " + table);
        }
        try (PreparedStatement query = connection.prepareStatement("SELECT count(*) FROM " + table)) {
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMapValue(String value) throws SQLException {
        if (value == null || value.trim().isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            Object decoded = mapper.readValue(value, Object.class);
            if (!(decoded instanceof Map)) {
                throw new SQLException("expected JSON object in runtime projection");
            }
            return new LinkedHashMap<String, Object>((Map<String, Object>) decoded);
        } catch (IOException exception) {
            throw new SQLException("cannot decode runtime projection JSON object", exception);
        }
    }

    private List<String> readStringListValue(String value) throws SQLException {
        if (value == null || value.trim().isEmpty()) {
            return new ArrayList<String>();
        }
        try {
            Object decoded = mapper.readValue(value, Object.class);
            if (!(decoded instanceof List)) {
                throw new SQLException("expected JSON array in runtime projection");
            }
            List<String> result = new ArrayList<String>();
            for (Object item : (List<?>) decoded) {
                result.add(String.valueOf(item));
            }
            return result;
        } catch (IOException exception) {
            throw new SQLException("cannot decode runtime projection JSON array", exception);
        }
    }

    private Map<String, String> readStringMapValue(String value) throws SQLException {
        Map<String, Object> decoded = readMapValue(value);
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Object> entry : decoded.entrySet()) {
            result.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return result;
    }

    private Object readJsonValue(String value) throws SQLException {
        if (value == null) {
            return null;
        }
        try {
            return mapper.readValue(value, Object.class);
        } catch (IOException exception) {
            throw new SQLException("cannot decode configuration JSON value", exception);
        }
    }

    private void addState(List<String> states, String value) {
        if (value != null && !states.contains(value)) {
            states.add(value);
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
