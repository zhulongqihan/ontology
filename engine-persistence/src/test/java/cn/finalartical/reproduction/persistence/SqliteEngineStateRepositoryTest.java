package cn.finalartical.reproduction.persistence;

import cn.finalartical.reproduction.admin.EngineAdminService;
import cn.finalartical.reproduction.admin.EngineField;
import cn.finalartical.reproduction.admin.EngineState;
import cn.finalartical.reproduction.admin.JsonEngineStateRepository;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

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
}
