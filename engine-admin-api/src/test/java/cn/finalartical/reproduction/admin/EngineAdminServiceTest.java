package cn.finalartical.reproduction.admin;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.ConcurrentModificationException;

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

    @Test
    public void runtimeBindsSchemaWorkflowSnapshotsAndTraceSpans() throws Exception {
        Path path = Files.createTempDirectory("engine-runtime-evidence").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("name", "confidence");
        field.put("type", "DECIMAL");
        service.addField("interview-session", field);
        Map<String, Object> transition = new LinkedHashMap<String, Object>();
        transition.put("fromState", "COMPLETED");
        transition.put("event", "archive");
        transition.put("toState", "ARCHIVED");
        service.addTransition("interview-session", transition);

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("modelId", "interview-session");
        payload.put("contextId", "ctx-evidence");
        payload.put("event", "startInterview");
        payload.put("idempotencyKey", "evidence-1");
        payload.put("values", new LinkedHashMap<String, Object>() {{ put("candidateName", "证据测试"); }});
        RuntimeRun run = service.execute(payload);

        assertEquals(3, run.getSchemaVersion());
        assertEquals(2, run.getWorkflowVersion());
        assertEquals(run.getId(), run.getTrace().getRunId());
        assertTrue(run.getTrace().isSealed());
        assertEquals(Arrays.asList("request", "validation", "workflow", "ontology", "persistence", "response"),
                spanNames(run));
        assertEquals("IN_INTERVIEW", service.context("ctx-evidence").getState());
        assertEquals(run.getAfterSnapshot().getSha256(), service.context("ctx-evidence").getLastSnapshotSha256());
    }

    @Test
    public void idempotentRequestReturnsTheOriginalRunWithoutAdvancingAgain() throws Exception {
        Path path = Files.createTempDirectory("engine-idempotency").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("modelId", "interview-session");
        payload.put("contextId", "ctx-idempotent");
        payload.put("event", "startInterview");
        payload.put("idempotencyKey", "same-request");
        payload.put("values", new LinkedHashMap<String, Object>() {{ put("candidateName", "只执行一次"); }});

        RuntimeRun first = service.execute(payload);
        RuntimeRun second = service.execute(payload);

        assertEquals(first.getId(), second.getId());
        assertEquals(1, service.runs().size());
        assertEquals(1L, service.context("ctx-idempotent").getRevision());
    }

    @Test
    public void failedRunDoesNotChangeThePersistedContext() throws Exception {
        Path path = Files.createTempDirectory("engine-rollback").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        Map<String, Object> first = new LinkedHashMap<String, Object>();
        first.put("modelId", "interview-session");
        first.put("contextId", "ctx-rollback");
        first.put("event", "startInterview");
        first.put("values", new LinkedHashMap<String, Object>() {{ put("candidateName", "不会污染"); }});
        RuntimeRun passed = service.execute(first);

        Map<String, Object> failed = new LinkedHashMap<String, Object>();
        failed.put("modelId", "interview-session");
        failed.put("contextId", "ctx-rollback");
        failed.put("event", "submitEvaluation");
        failed.put("values", new LinkedHashMap<String, Object>() {{ put("unexpected", true); }});
        RuntimeRun rejected = service.execute(failed);

        assertEquals("FAILED", rejected.getStatus());
        assertEquals("IN_INTERVIEW", rejected.getToState());
        assertEquals("IN_INTERVIEW", service.context("ctx-rollback").getState());
        assertEquals(1L, service.context("ctx-rollback").getRevision());
        assertTrue(passed.getBeforeSnapshot().getSha256() != null);
        assertTrue(!passed.getBeforeSnapshot().getSha256().equals(passed.getAfterSnapshot().getSha256()));
    }

    @Test
    public void failedRunCanBeRetriedAsANewAttempt() throws Exception {
        Path path = Files.createTempDirectory("engine-retry").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("modelId", "interview-session");
        payload.put("contextId", "ctx-retry");
        payload.put("event", "submitEvaluation");
        payload.put("values", new LinkedHashMap<String, Object>());

        RuntimeRun failed = service.execute(payload);
        RuntimeRun retried = service.retry(failed.getId());

        assertEquals("FAILED", retried.getStatus());
        assertEquals(failed.getId(), retried.getRetryOfRunId());
        assertEquals(2, retried.getAttempt());
        assertTrue(!failed.getId().equals(retried.getId()));
        assertEquals(2, service.runs().size());
    }

    @Test
    public void passedRunCanBeRolledBackOnlyAtTheLatestContextRevision() throws Exception {
        Path path = Files.createTempDirectory("engine-run-rollback").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("candidateName", "回滚测试");
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("modelId", "interview-session");
        payload.put("contextId", "ctx-run-rollback");
        payload.put("event", "startInterview");
        payload.put("values", values);
        RuntimeRun passed = service.execute(payload);

        RuntimeRun rollback = service.rollback(passed.getId());

        assertEquals("ROLLED_BACK", rollback.getStatus());
        assertEquals("PENDING_INTERVIEW", rollback.getToState());
        assertEquals("PENDING_INTERVIEW", service.context("ctx-run-rollback").getState());
        assertEquals("ROLLED_BACK", service.context("ctx-run-rollback").getStatus());
        assertEquals(2L, service.context("ctx-run-rollback").getRevision());
        assertEquals(rollback.getId(), service.context("ctx-run-rollback").getLastRunId());
        assertEquals("RUN_ROLLED_BACK", service.auditEvents().get(0).getAction());

        try {
            service.rollback(passed.getId());
        } catch (ConcurrentModificationException expected) {
            assertTrue(expected.getMessage().contains("newer context revision"));
            return;
        }
        throw new AssertionError("a rollback must be rejected after the context revision changes");
    }

    @Test
    public void configurationChangesProduceAuditEventsAndStaleWritersAreRejected() throws Exception {
        Path path = Files.createTempDirectory("engine-concurrency").resolve("state.json");
        EngineAdminService first = new EngineAdminService(new JsonEngineStateRepository(path));
        EngineAdminService stale = new EngineAdminService(new JsonEngineStateRepository(path));
        Map<String, Object> model = new LinkedHashMap<String, Object>();
        model.put("id", "concurrency-model");
        model.put("name", "并发模型");
        first.addModel(model);
        assertTrue(first.auditEvents().size() >= 1);

        try {
            stale.addModel(new LinkedHashMap<String, Object>() {{ put("id", "stale-model"); put("name", "过期写入"); }});
        } catch (ConcurrentModificationException expected) {
            assertTrue(expected.getMessage().contains("revision conflict"));
            return;
        }
        throw new AssertionError("stale writer must be rejected");
    }

    @Test
    public void questionnaireRuntimeCanEmitAnObjectGraph() throws Exception {
        Path path = Files.createTempDirectory("engine-ontology-runtime").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("name", "subjects");
        field.put("type", "JSON");
        service.addField("questionnaire", field);
        Map<String, Object> subject = new LinkedHashMap<String, Object>();
        subject.put("id", "s-001");
        subject.put("title", "集合");
        subject.put("options", Arrays.<Object>asList(new LinkedHashMap<String, Object>() {{
            put("id", "o-001"); put("label", "List");
        }}));
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("name", "Java 基础");
        values.put("subjectId", "subject-001");
        values.put("subjects", Arrays.<Object>asList(subject));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("modelId", "questionnaire");
        payload.put("contextId", "ctx-questionnaire");
        payload.put("event", "publish");
        payload.put("values", values);

        RuntimeRun run = service.execute(payload);

        assertEquals("PASSED", run.getStatus());
        assertEquals(3, ((List<?>) run.getOntologyGraph().get("objects")).size());
        assertEquals(2, ((List<?>) run.getOntologyGraph().get("relations")).size());
    }

    private static List<String> spanNames(RuntimeRun run) {
        List<String> names = new ArrayList<String>();
        for (TraceSpanRecord span : run.getTrace().getSpans()) {
            names.add(span.getName());
        }
        return names;
    }
}
