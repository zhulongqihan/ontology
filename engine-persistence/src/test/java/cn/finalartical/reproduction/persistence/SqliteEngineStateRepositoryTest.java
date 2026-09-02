package cn.finalartical.reproduction.persistence;

import cn.finalartical.reproduction.admin.EngineAdminService;
import cn.finalartical.reproduction.admin.EngineField;
import cn.finalartical.reproduction.admin.EngineState;
import cn.finalartical.reproduction.admin.JsonEngineStateRepository;
import cn.finalartical.reproduction.admin.RuntimeRun;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ConcurrentModificationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

public class SqliteEngineStateRepositoryTest {
    @Test
    public void persistsEngineStateAcrossRepositoryReload() throws Exception {
        Path directory = Files.createTempDirectory("engine-sqlite");
        SqliteEngineStateRepository repository = new SqliteEngineStateRepository(directory.resolve("engine.db"));
        EngineAdminService service = new EngineAdminService(repository);
        Map<String, Object> fieldPayload = new LinkedHashMap<String, Object>();
        fieldPayload.put("name", "confidence");
        fieldPayload.put("type", "DECIMAL");
        EngineField field = service.addField("interview-session", fieldPayload);

        EngineState reloaded = new SqliteEngineStateRepository(directory.resolve("engine.db")).load();

        assertEquals("confidence", field.getName());
        assertEquals(5, reloaded.getModels().get(0).getFields().size());
        assertEquals(1L, reloaded.getAuditEvents().get(0).getBeforeRevision());
        assertEquals(2L, reloaded.getAuditEvents().get(0).getAfterRevision());
        assertEquals(2, reloaded.getAuditEvents().get(0).getChanges().size());
        assertEquals("schema.fields[confidence]", reloaded.getAuditEvents().get(0).getChanges().get(1).getPath());
        assertTrue(Files.size(directory.resolve("engine.db")) > 0);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("engine.db").toAbsolutePath());
             ResultSet result = connection.createStatement().executeQuery(
                     "SELECT (SELECT max(version) FROM schema_version), (SELECT count(*) FROM engine_model), " +
                             "(SELECT count(*) FROM schema_field WHERE field_name = 'confidence')")) {
            assertTrue(result.next());
            assertEquals(11, result.getInt(1));
            assertEquals(2, result.getInt(2));
            assertTrue(result.getInt(3) >= 1);
        }
    }

    @Test
    public void restoresOnlyAValidatedBackupAndLeavesLiveDatabaseUntouchedOnFailure() throws Exception {
        Path directory = Files.createTempDirectory("engine-sqlite-restore");
        Path database = directory.resolve("engine.db");
        Path backup = directory.resolve("backup.db");
        Path corruptBackup = directory.resolve("corrupt-backup.db");
        SqliteEngineStateRepository repository = new SqliteEngineStateRepository(database);
        EngineAdminService service = new EngineAdminService(repository);

        service.addField("interview-session", new LinkedHashMap<String, Object>() {{
            put("name", "backupField");
            put("type", "STRING");
        }});
        repository.backupTo(backup);
        service.addField("interview-session", new LinkedHashMap<String, Object>() {{
            put("name", "laterField");
            put("type", "STRING");
        }});

        repository.restoreFrom(backup);

        EngineState restored = new SqliteEngineStateRepository(database).load();
        assertEquals(3, findModel(restored, "interview-session").getSchemaVersion());
        assertTrue(hasField(restored, "interview-session", "backupField"));
        assertTrue(!hasField(restored, "interview-session", "laterField"));

        Files.copy(backup, corruptBackup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + corruptBackup.toAbsolutePath());
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE engine_model SET schema_version = ? WHERE model_id = ?")) {
            update.setInt(1, 999);
            update.setString(2, "interview-session");
            update.executeUpdate();
        }

        try {
            repository.restoreFrom(corruptBackup);
            fail("corrupt backup must not replace the live database");
        } catch (IllegalStateException exception) {
            assertTrue(exception.getMessage().contains("cannot restore SQLite engine state"));
        }

        EngineState stillLive = new SqliteEngineStateRepository(database).load();
        assertEquals(3, findModel(stillLive, "interview-session").getSchemaVersion());
        assertTrue(hasField(stillLive, "interview-session", "backupField"));
    }

    @Test
    public void importsExistingJsonStateOnlyWhenDatabaseIsEmpty() throws Exception {
        Path directory = Files.createTempDirectory("engine-sqlite-migration");
        Path legacy = directory.resolve("engine-state.json");
        EngineState legacyState = new JsonEngineStateRepository(legacy).load();
        SqliteEngineStateRepository repository = new SqliteEngineStateRepository(directory.resolve("engine.db"), legacy);

        EngineState imported = repository.load();

        assertEquals(legacyState.getEngineId(), imported.getEngineId());
        assertEquals(2, imported.getModels().size());
    }

    @Test
    public void readsConfigurationFromNormalizedTablesBeforeCompatibilityPayload() throws Exception {
        Path directory = Files.createTempDirectory("engine-sqlite-normalized-read");
        Path database = directory.resolve("engine.db");
        SqliteEngineStateRepository repository = new SqliteEngineStateRepository(database);
        repository.load();

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            connection.createStatement().executeUpdate(
                    "UPDATE engine_model SET name = '从规范化表读取' WHERE model_id = 'interview-session'");
        }

        EngineState reloaded = repository.load();

        assertEquals("从规范化表读取", findModel(reloaded, "interview-session").getName());
    }

    @Test
    public void rejectsBrokenNormalizedProjectionBeforeRewritingCompatibilityPayload() throws Exception {
        Path directory = Files.createTempDirectory("engine-sqlite-projection-integrity");
        Path database = directory.resolve("engine.db");
        SqliteEngineStateRepository repository = new SqliteEngineStateRepository(database);
        repository.load();

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE engine_model SET schema_version = ? WHERE model_id = ?")) {
            update.setInt(1, 999);
            update.setString(2, "interview-session");
            update.executeUpdate();
        }

        try {
            repository.load();
            fail("broken normalized projection must not be silently rewritten");
        } catch (IllegalStateException exception) {
            assertTrue(exception.getCause().getMessage().contains("missing current schema definition"));
        }
    }

    @Test
    public void rejectsAFieldVersionThatCannotBelongToItsSchema() throws Exception {
        Path directory = Files.createTempDirectory("engine-sqlite-field-integrity");
        Path database = directory.resolve("engine.db");
        SqliteEngineStateRepository repository = new SqliteEngineStateRepository(database);
        repository.load();

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE schema_field SET field_version = ? WHERE model_id = ? AND schema_version = ? AND field_name = ?")) {
            update.setInt(1, 999);
            update.setString(2, "interview-session");
            update.setInt(3, 1);
            update.setString(4, "candidateName");
            update.executeUpdate();
        }

        try {
            repository.load();
            fail("invalid field version must be rejected");
        } catch (IllegalStateException exception) {
            assertTrue(exception.getCause().getMessage().contains("schema_field version is newer"));
        }
    }

    @Test
    public void rejectsRuntimeRunWithMismatchedTraceIdentity() throws Exception {
        Path directory = Files.createTempDirectory("engine-sqlite-trace-integrity");
        Path database = directory.resolve("engine.db");
        EngineAdminService service = new EngineAdminService(new SqliteEngineStateRepository(database));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("modelId", "interview-session");
        payload.put("contextId", "ctx-trace-integrity");
        payload.put("event", "startInterview");
        RuntimeRun run = service.execute(payload);

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE runtime_run SET trace_id = ? WHERE run_id = ?")) {
            update.setString(1, "trace-does-not-match");
            update.setString(2, run.getId());
            update.executeUpdate();
        }

        try {
            new SqliteEngineStateRepository(database).load();
            fail("runtime trace mismatch must be rejected");
        } catch (IllegalStateException exception) {
            assertTrue(exception.getCause().getMessage().contains("mismatched trace"));
        }
    }

    @Test
    public void rejectsMalformedAuditChangeSetBeforeRewritingCompatibilityPayload() throws Exception {
        Path directory = Files.createTempDirectory("engine-sqlite-audit-integrity");
        Path database = directory.resolve("engine.db");
        SqliteEngineStateRepository repository = new SqliteEngineStateRepository(database);
        EngineAdminService service = new EngineAdminService(repository);
        service.addField("interview-session", new LinkedHashMap<String, Object>() {{
            put("name", "auditField");
            put("type", "STRING");
        }});

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE audit_event SET changes_json = ? WHERE action = ?")) {
            update.setString(1, "{\"not\":\"an-array\"}");
            update.setString(2, "SCHEMA_PUBLISHED");
            update.executeUpdate();
        }

        try {
            repository.load();
            fail("malformed audit change set must be rejected");
        } catch (IllegalStateException exception) {
            assertTrue(exception.getCause().getMessage().contains("audit change set"));
        }
    }

    @Test
    public void preservesRuntimeHistoryWhileRebuildingMissingConfigurationProjection() throws Exception {
        Path directory = Files.createTempDirectory("engine-sqlite-legacy-runtime");
        Path database = directory.resolve("engine.db");
        SqliteEngineStateRepository repository = new SqliteEngineStateRepository(database);
        EngineAdminService service = new EngineAdminService(repository);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("modelId", "interview-session");
        payload.put("contextId", "ctx-legacy-projection");
        payload.put("event", "startInterview");
        RuntimeRun written = service.execute(payload);

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            connection.createStatement().execute("PRAGMA foreign_keys = ON");
            connection.createStatement().executeUpdate("DELETE FROM engine_model");
        }

        EngineState recovered = repository.load();

        assertEquals(2, recovered.getModels().size());
        assertEquals(written.getId(), new EngineAdminService(new SqliteEngineStateRepository(database))
                .run(written.getId()).getId());
    }

    @Test
    public void persistsExplicitSchemaMigrationInNormalizedTables() throws Exception {
        Path directory = Files.createTempDirectory("engine-sqlite-schema-migration");
        Path database = directory.resolve("engine.db");
        EngineAdminService service = new EngineAdminService(new SqliteEngineStateRepository(database));

        service.renameField("interview-session", new LinkedHashMap<String, Object>() {{
            put("sourceName", "candidateName");
            put("targetName", "applicantName");
        }});

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             ResultSet result = connection.createStatement().executeQuery(
                     "SELECT from_version, to_version, source_field, target_field FROM schema_migration")) {
            assertTrue(result.next());
            assertEquals(2, result.getInt(1));
            assertEquals(3, result.getInt(2));
            assertEquals("candidateName", result.getString(3));
            assertEquals("applicantName", result.getString(4));
        }

        EngineAdminService reloaded = new EngineAdminService(new SqliteEngineStateRepository(database));
        assertEquals(1, reloaded.model("interview-session").getSchemaMigrations().size());
        assertEquals("applicantName", reloaded.model("interview-session").getFields().get(0).getName());
    }

    @Test
    public void runtimeDomainProjectionSurvivesRepositoryRestart() throws Exception {
        Path directory = Files.createTempDirectory("engine-sqlite-runtime");
        Path database = directory.resolve("engine.db");
        SqliteEngineStateRepository repository = new SqliteEngineStateRepository(database);
        EngineAdminService service = new EngineAdminService(repository);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("modelId", "interview-session");
        payload.put("contextId", "ctx-restart");
        payload.put("event", "startInterview");
        payload.put("idempotencyKey", "restart-1");
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("candidateName", "重启恢复");
        payload.put("values", values);
        RuntimeRun written = service.execute(payload);

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             PreparedStatement contextUpdate = connection.prepareStatement(
                     "UPDATE runtime_context SET state = ?, status = ? WHERE context_id = ?");
             PreparedStatement runUpdate = connection.prepareStatement(
                     "UPDATE runtime_run SET status = ?, error_code = ?, to_state = ? WHERE run_id = ?");
             PreparedStatement spanUpdate = connection.prepareStatement(
                     "UPDATE trace_span SET attributes_json = ? WHERE trace_id = ? AND name = ?")) {
            contextUpdate.setString(1, "DIRECT_READ_STATE");
            contextUpdate.setString(2, "DIRECT_READ_STATUS");
            contextUpdate.setString(3, "ctx-restart");
            contextUpdate.executeUpdate();
            runUpdate.setString(1, "DIRECT_READ_RUN_STATUS");
            runUpdate.setString(2, "direct-read-error");
            runUpdate.setString(3, "DIRECT_READ_STATE");
            runUpdate.setString(4, written.getId());
            runUpdate.executeUpdate();
            spanUpdate.setString(1, "{\"source\":\"normalized\"}");
            spanUpdate.setString(2, written.getTraceId());
            spanUpdate.setString(3, "request");
            assertEquals(1, spanUpdate.executeUpdate());
        }

        EngineAdminService reloaded = new EngineAdminService(new SqliteEngineStateRepository(database));
        RuntimeRun restored = reloaded.run(written.getId());

        assertEquals("DIRECT_READ_STATE", reloaded.context("ctx-restart").getState());
        assertEquals("DIRECT_READ_STATUS", reloaded.context("ctx-restart").getStatus());
        assertEquals("DIRECT_READ_RUN_STATUS", restored.getStatus());
        assertEquals("direct-read-error", restored.getErrorCode());
        assertEquals("DIRECT_READ_STATE", restored.getToState());
        assertEquals(written.getAfterSnapshot().getSha256(), restored.getAfterSnapshot().getSha256());
        assertEquals(7, restored.getTrace().getSpans().size());
        assertEquals("normalized", restored.getTrace().getSpans().get(0).getAttributes().get("source"));
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             ResultSet result = connection.createStatement().executeQuery(
                     "SELECT (SELECT count(*) FROM runtime_run), (SELECT count(*) FROM execution_snapshot), (SELECT count(*) FROM trace_span), (SELECT count(*) FROM idempotency_record)")) {
            assertTrue(result.next());
            assertTrue(result.getInt(1) >= 1);
            assertEquals(2, result.getInt(2));
            assertEquals(7, result.getInt(3));
            assertEquals(1, result.getInt(4));
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             ResultSet result = connection.createStatement().executeQuery(
                     "SELECT (SELECT max(version) FROM schema_version), input_values_json, attempt, retry_of_run_id FROM runtime_run WHERE run_id = '" + written.getId() + "'")) {
            assertTrue(result.next());
            assertEquals(11, result.getInt(1));
            assertTrue(result.getString(2).contains("重启恢复"));
            assertEquals(1, result.getInt(3));
            assertEquals(null, result.getString(4));
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             ResultSet result = connection.createStatement().executeQuery(
                     "SELECT (SELECT count(*) FROM engine_model), (SELECT count(*) FROM schema_definition), " +
                             "(SELECT count(*) FROM schema_field WHERE field_name = 'candidateName'), " +
                             "(SELECT count(*) FROM workflow_transition), (SELECT count(*) FROM ontology_type), " +
                             "(SELECT count(*) FROM service_registration)")) {
            assertTrue(result.next());
            assertEquals(2, result.getInt(1));
            assertTrue(result.getInt(2) >= 2);
            assertTrue(result.getInt(3) >= 1);
            assertTrue(result.getInt(4) >= 2);
            assertTrue(result.getInt(5) >= 2);
            assertTrue(result.getInt(6) >= 2);
        }
    }

    @Test
    public void serializesCrossInstanceWritersAndAllowsIdempotentRecoveryAfterConflict() throws Exception {
        Path directory = Files.createTempDirectory("engine-sqlite-concurrency");
        Path database = directory.resolve("engine.db");
        new SqliteEngineStateRepository(database).load();
        final EngineAdminService first = new EngineAdminService(new SqliteEngineStateRepository(database));
        final EngineAdminService second = new EngineAdminService(new SqliteEngineStateRepository(database));
        final Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("modelId", "interview-session");
        payload.put("contextId", "ctx-concurrent-idempotency");
        payload.put("event", "startInterview");
        payload.put("idempotencyKey", "cross-instance-1");
        payload.put("values", new LinkedHashMap<String, Object>() {{
            put("candidateName", "并发恢复");
        }});

        final CountDownLatch ready = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<RuntimeRun>> futures = new ArrayList<Future<RuntimeRun>>();
        futures.add(executor.submit(() -> {
            ready.countDown();
            ready.await();
            return first.execute(payload);
        }));
        futures.add(executor.submit(() -> {
            ready.countDown();
            ready.await();
            return second.execute(payload);
        }));

        int successes = 0;
        int conflicts = 0;
        for (Future<RuntimeRun> future : futures) {
            try {
                assertEquals("PASSED", future.get().getStatus());
                successes++;
            } catch (Exception exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                if (cause instanceof ConcurrentModificationException) {
                    conflicts++;
                } else {
                    throw exception;
                }
            }
        }
        executor.shutdownNow();

        assertEquals(1, successes);
        assertEquals(1, conflicts);
        EngineAdminService recovered = new EngineAdminService(new SqliteEngineStateRepository(database));
        RuntimeRun replayed = recovered.execute(payload);
        assertEquals(1, recovered.runs().size());
        assertEquals("PASSED", replayed.getStatus());
        assertEquals("cross-instance-1", replayed.getIdempotencyKey());
        assertEquals("COMMITTED", replayed.getTrace().getLifecycle());
    }

    @Test
    public void retainsEvidenceIncompleteRuntimeRowsAsExplicitLegacyRecords() throws Exception {
        Path directory = Files.createTempDirectory("engine-sqlite-legacy-runtime-row");
        Path database = directory.resolve("engine.db");
        EngineAdminService service = new EngineAdminService(new SqliteEngineStateRepository(database));
        RuntimeRun written = service.execute(new LinkedHashMap<String, Object>() {{
            put("modelId", "interview-session");
            put("contextId", "ctx-legacy-runtime-row");
            put("event", "startInterview");
        }});

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE runtime_run SET engine_version = '', schema_version = 0, workflow_version = 0 "
                             + "WHERE run_id = ?");
             PreparedStatement deleteSpans = connection.prepareStatement(
                     "DELETE FROM trace_span WHERE trace_id = ?");
             PreparedStatement deleteTrace = connection.prepareStatement(
                     "DELETE FROM trace WHERE run_id = ?")) {
            update.setString(1, written.getId());
            update.executeUpdate();
            deleteSpans.setString(1, written.getTraceId());
            deleteSpans.executeUpdate();
            deleteTrace.setString(1, written.getId());
            deleteTrace.executeUpdate();
        }

        EngineAdminService reloaded = new EngineAdminService(new SqliteEngineStateRepository(database));

        assertEquals(EngineAdminService.LEGACY_RUNTIME_IDENTITY, reloaded.run(written.getId()).getDataIdentity());
        assertEquals(null, reloaded.run(written.getId()).getTrace());
    }

    private static cn.finalartical.reproduction.admin.EngineModel findModel(EngineState state, String modelId) {
        for (cn.finalartical.reproduction.admin.EngineModel model : state.getModels()) {
            if (modelId.equals(model.getId())) {
                return model;
            }
        }
        throw new AssertionError("model not found: " + modelId);
    }

    private static boolean hasField(EngineState state, String modelId, String fieldName) {
        for (EngineField field : findModel(state, modelId).getFields()) {
            if (fieldName.equals(field.getName())) {
                return true;
            }
        }
        return false;
    }
}
