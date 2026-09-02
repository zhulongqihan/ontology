package cn.finalartical.reproduction.persistence;

import cn.finalartical.reproduction.admin.EngineAdminService;
import cn.finalartical.reproduction.admin.EngineField;
import cn.finalartical.reproduction.admin.EngineState;
import cn.finalartical.reproduction.admin.JsonEngineStateRepository;
import cn.finalartical.reproduction.admin.RuntimeRun;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.junit.Assert.assertEquals;
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
        assertTrue(Files.size(directory.resolve("engine.db")) > 0);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("engine.db").toAbsolutePath());
             ResultSet result = connection.createStatement().executeQuery(
                     "SELECT (SELECT max(version) FROM schema_version), (SELECT count(*) FROM engine_model), " +
                             "(SELECT count(*) FROM schema_field WHERE field_name = 'confidence')")) {
            assertTrue(result.next());
            assertEquals(4, result.getInt(1));
            assertEquals(2, result.getInt(2));
            assertTrue(result.getInt(3) >= 1);
        }
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

        EngineAdminService reloaded = new EngineAdminService(new SqliteEngineStateRepository(database));
        RuntimeRun restored = reloaded.run(written.getId());

        assertEquals("IN_INTERVIEW", reloaded.context("ctx-restart").getState());
        assertEquals(written.getAfterSnapshot().getSha256(), restored.getAfterSnapshot().getSha256());
        assertEquals(6, restored.getTrace().getSpans().size());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             ResultSet result = connection.createStatement().executeQuery(
                     "SELECT (SELECT count(*) FROM runtime_run), (SELECT count(*) FROM execution_snapshot), (SELECT count(*) FROM trace_span), (SELECT count(*) FROM idempotency_record)")) {
            assertTrue(result.next());
            assertTrue(result.getInt(1) >= 1);
            assertEquals(2, result.getInt(2));
            assertEquals(6, result.getInt(3));
            assertEquals(1, result.getInt(4));
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             ResultSet result = connection.createStatement().executeQuery(
                     "SELECT (SELECT max(version) FROM schema_version), input_values_json, attempt, retry_of_run_id FROM runtime_run WHERE run_id = '" + written.getId() + "'")) {
            assertTrue(result.next());
            assertEquals(4, result.getInt(1));
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

    private static cn.finalartical.reproduction.admin.EngineModel findModel(EngineState state, String modelId) {
        for (cn.finalartical.reproduction.admin.EngineModel model : state.getModels()) {
            if (modelId.equals(model.getId())) {
                return model;
            }
        }
        throw new AssertionError("model not found: " + modelId);
    }
}
