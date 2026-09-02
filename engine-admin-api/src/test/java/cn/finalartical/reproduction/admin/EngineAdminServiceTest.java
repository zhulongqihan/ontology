package cn.finalartical.reproduction.admin;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EngineAdminServiceTest {
    @Test
    public void modelRegistryAcceptsANewRuntimeModel() throws Exception {
        Path path = Files.createTempDirectory("engine-admin").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", "assessment-session");
        payload.put("name", "评估会话");
        payload.put("description", "动态评估对象");
        payload.put("initialState", "DRAFT");

        EngineModel model = service.addModel(payload);

        assertEquals("assessment-session", model.getId());
        assertEquals("DRAFT", model.getInitialState());
        assertEquals(3, service.models().size());
    }

    @Test
    public void stateIsPersistedWhenAnAdminAddsAField() throws Exception {
        Path path = Files.createTempDirectory("engine-admin").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("name", "confidence");
        payload.put("type", "DECIMAL");
        payload.put("required", false);

        EngineField field = service.addField("interview-session", payload);

        assertEquals("confidence", field.getName());
        assertEquals(3, service.model("interview-session").getSchemaVersion());
        EngineAdminService reloaded = new EngineAdminService(new JsonEngineStateRepository(path));
        assertEquals(5, reloaded.model("interview-session").getFields().size());
    }

    @Test
    public void runtimeUsesTheConfiguredSchemaAndWorkflow() throws Exception {
        Path path = Files.createTempDirectory("engine-admin").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("candidateName", "小羊");
        values.put("score", 95);
        payload.put("modelId", "interview-session");
        payload.put("event", "startInterview");
        payload.put("values", values);

        RuntimeRun run = service.execute(payload);

        assertEquals("PASSED", run.getStatus());
        assertEquals("PENDING_INTERVIEW", run.getFromState());
        assertEquals("IN_INTERVIEW", run.getToState());
        assertTrue(run.getValidationErrors().isEmpty());
        assertEquals(1, service.runs().size());
    }

    @Test
    public void runtimeRejectsMissingRequiredFieldAndIllegalEvent() throws Exception {
        Path path = Files.createTempDirectory("engine-admin").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("modelId", "interview-session");
        payload.put("event", "submitEvaluation");

        RuntimeRun run = service.execute(payload);

        assertEquals("FAILED", run.getStatus());
        assertEquals(1, run.getValidationErrors().size());
        assertTrue(run.getValidationErrors().get(0).contains("candidateName"));
    }

    @Test
    public void runtimeContinuesFromThePreviousSnapshotWhenContextIsReused() throws Exception {
        Path path = Files.createTempDirectory("engine-admin").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        Map<String, Object> first = new LinkedHashMap<String, Object>();
        Map<String, Object> firstValues = new LinkedHashMap<String, Object>();
        firstValues.put("candidateName", "小羊");
        first.put("modelId", "interview-session");
        first.put("contextId", "ctx-continuous");
        first.put("event", "startInterview");
        first.put("values", firstValues);
        RuntimeRun started = service.execute(first);

        Map<String, Object> second = new LinkedHashMap<String, Object>();
        Map<String, Object> secondValues = new LinkedHashMap<String, Object>();
        secondValues.put("evaluationScore", 92);
        second.put("modelId", "interview-session");
        second.put("contextId", "ctx-continuous");
        second.put("event", "submitEvaluation");
        second.put("values", secondValues);
        RuntimeRun completed = service.execute(second);

        assertEquals("ctx-continuous", started.getContextId());
        assertEquals("IN_INTERVIEW", completed.getFromState());
        assertEquals("COMPLETED", completed.getToState());
        assertEquals("小羊", completed.getValues().get("candidateName"));
    }

    @Test
    public void ontologyRelationsAndServicesCanBeRegistered() throws Exception {
        Path path = Files.createTempDirectory("engine-admin").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        Map<String, Object> relation = new LinkedHashMap<String, Object>();
        relation.put("name", "containsOption");
        relation.put("targetType", "option");
        relation.put("cardinality", "1:N");
        service.addOntologyRelation("subject", relation);

        Map<String, Object> registration = new LinkedHashMap<String, Object>();
        registration.put("id", "assessment-provider");
        registration.put("name", "Assessment Provider");
        registration.put("provider", "LocalServiceRegistry");
        registration.put("endpoint", "local://assessment-provider");
        service.addService(registration);

        assertEquals(2, service.ontologyTypes().get(1).getRelations().size());
        assertEquals(3, service.services().size());
    }

    @Test
    public void legacyProductIdentityIsMigratedOnLoad() throws Exception {
        Path path = Files.createTempDirectory("engine-admin").resolve("state.json");
        JsonEngineStateRepository repository = new JsonEngineStateRepository(path);
        EngineState legacy = DefaultEngineSeed.create();
        legacy.setEngineId("flexible-engine-reproduction");
        legacy.setEngineName("柔性引擎复现实例");
        legacy.setEngineVersion("0.2.0");
        RuntimeRun oldRun = new RuntimeRun();
        oldRun.setDataIdentity("REPRODUCED_SYSTEM_RUN");
        legacy.getRuns().add(oldRun);
        repository.save(legacy);

        EngineAdminService service = new EngineAdminService(repository);
        Map<String, Object> engine = (Map<String, Object>) service.overview().get("engine");

        assertEquals("flexible-engine-ontology", engine.get("id"));
        assertEquals("柔性引擎与本体化平台", engine.get("name"));
        assertEquals("0.3.0", engine.get("version"));
        assertEquals("ENGINE_RUNTIME_RESULT", service.runs().get(0).getDataIdentity());
    }
}
