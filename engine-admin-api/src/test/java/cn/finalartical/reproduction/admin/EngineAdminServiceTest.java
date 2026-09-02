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
    public void runtimeMigratesAStaleContextToTheCurrentSchemaBeforeValidation() throws Exception {
        Path path = Files.createTempDirectory("engine-schema-migration").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        Map<String, Object> first = new LinkedHashMap<String, Object>();
        first.put("modelId", "interview-session");
        first.put("contextId", "ctx-schema-migration");
        first.put("event", "startInterview");
        first.put("values", new LinkedHashMap<String, Object>() {{ put("candidateName", "迁移测试"); }});
        RuntimeRun started = service.execute(first);

        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("name", "riskLevel");
        field.put("type", "STRING");
        field.put("required", true);
        field.put("defaultValue", "LOW");
        service.addField("interview-session", field);

        Map<String, Object> second = new LinkedHashMap<String, Object>();
        second.put("modelId", "interview-session");
        second.put("contextId", "ctx-schema-migration");
        second.put("event", "submitEvaluation");
        second.put("values", new LinkedHashMap<String, Object>() {{ put("evaluationScore", 92); }});
        RuntimeRun completed = service.execute(second);

        assertEquals(2, started.getSchemaVersion());
        assertEquals(3, completed.getSchemaVersion());
        assertEquals("PASSED", completed.getStatus());
        assertEquals("LOW", completed.getValues().get("riskLevel"));
        assertEquals(2, completed.getBeforeSnapshot().getSchemaVersion());
        assertEquals(3, completed.getAfterSnapshot().getSchemaVersion());
        assertEquals("true", completed.getTrace().getSpans().get(1).getAttributes().get("schemaMigrationApplied"));
        assertEquals(3, service.context("ctx-schema-migration").getSchemaVersion());
    }

    @Test
    public void failedSchemaMigrationDoesNotAdvanceTheContext() throws Exception {
        Path path = Files.createTempDirectory("engine-schema-migration-failure").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        Map<String, Object> first = new LinkedHashMap<String, Object>();
        first.put("modelId", "interview-session");
        first.put("contextId", "ctx-schema-migration-failure");
        first.put("event", "startInterview");
        first.put("values", new LinkedHashMap<String, Object>() {{ put("candidateName", "迁移失败"); }});
        service.execute(first);

        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("name", "approvalCode");
        field.put("type", "STRING");
        field.put("required", true);
        service.addField("interview-session", field);

        Map<String, Object> second = new LinkedHashMap<String, Object>();
        second.put("modelId", "interview-session");
        second.put("contextId", "ctx-schema-migration-failure");
        second.put("event", "submitEvaluation");
        RuntimeRun rejected = service.execute(second);

        assertEquals("FAILED", rejected.getStatus());
        assertEquals("VALIDATION_ERROR", rejected.getErrorCode());
        assertEquals(2, service.context("ctx-schema-migration-failure").getSchemaVersion());
        assertEquals(2, rejected.getBeforeSnapshot().getSchemaVersion());
        assertEquals(3, rejected.getAfterSnapshot().getSchemaVersion());
    }

    @Test
    public void runtimeAppliesAnExplicitRenamedFieldMigrationAcrossVersions() throws Exception {
        Path path = Files.createTempDirectory("engine-schema-rename").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        Map<String, Object> first = new LinkedHashMap<String, Object>();
        first.put("modelId", "interview-session");
        first.put("contextId", "ctx-schema-rename");
        first.put("event", "startInterview");
        first.put("values", new LinkedHashMap<String, Object>() {{ put("candidateName", "改名迁移"); }});
        service.execute(first);

        service.renameField("interview-session", new LinkedHashMap<String, Object>() {{
            put("sourceName", "candidateName");
            put("targetName", "applicantName");
        }});

        Map<String, Object> second = new LinkedHashMap<String, Object>();
        second.put("modelId", "interview-session");
        second.put("contextId", "ctx-schema-rename");
        second.put("event", "submitEvaluation");
        RuntimeRun completed = service.execute(second);

        assertEquals("PASSED", completed.getStatus());
        assertEquals("改名迁移", completed.getValues().get("applicantName"));
        assertTrue(!completed.getValues().containsKey("candidateName"));
        assertEquals("applicantName", service.model("interview-session").getFields().get(0).getName());
        assertEquals(1, service.model("interview-session").getSchemaMigrations().size());
    }

    @Test
    public void runtimeDropsAFieldRemovedByTheTargetSchema() throws Exception {
        Path path = Files.createTempDirectory("engine-schema-removal").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        Map<String, Object> first = new LinkedHashMap<String, Object>();
        first.put("modelId", "interview-session");
        first.put("contextId", "ctx-schema-removal");
        first.put("event", "startInterview");
        first.put("values", new LinkedHashMap<String, Object>() {{
            put("candidateName", "删除迁移");
            put("score", 88);
        }});
        service.execute(first);

        service.removeField("interview-session", new LinkedHashMap<String, Object>() {{
            put("name", "score");
        }});

        Map<String, Object> second = new LinkedHashMap<String, Object>();
        second.put("modelId", "interview-session");
        second.put("contextId", "ctx-schema-removal");
        second.put("event", "submitEvaluation");
        RuntimeRun completed = service.execute(second);

        assertEquals("PASSED", completed.getStatus());
        assertTrue(!completed.getValues().containsKey("score"));
        assertEquals(3, completed.getAfterSnapshot().getSchemaVersion());
        assertEquals(3, service.context("ctx-schema-removal").getSchemaVersion());
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
        assertEquals("REPRODUCED_SYSTEM_RUN", service.runs().get(0).getDataIdentity());
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
        assertEquals(Arrays.asList("request", "validation", "workflow", "ontology", "provider", "persistence", "response"),
                spanNames(run));
        assertEquals("SKIPPED", run.getTrace().getSpans().get(4).getStatus());
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
    public void idempotencyBindsOntologyInputAsPartOfTheRequest() throws Exception {
        Path path = Files.createTempDirectory("engine-idempotency-ontology").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("name", "幂等本体");
        values.put("subjectId", "subject-001");
        Map<String, Object> first = new LinkedHashMap<String, Object>();
        first.put("modelId", "questionnaire");
        first.put("contextId", "ctx-idempotent-ontology");
        first.put("event", "publish");
        first.put("idempotencyKey", "same-ontology-request");
        first.put("values", values);
        first.put("ontology", new LinkedHashMap<String, Object>() {{ put("source", "first"); }});
        service.execute(first);

        Map<String, Object> second = new LinkedHashMap<String, Object>(first);
        second.put("ontology", new LinkedHashMap<String, Object>() {{ put("source", "second"); }});
        try {
            service.execute(second);
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("different request"));
            return;
        }
        throw new AssertionError("idempotency must include ontology input");
    }

    @Test
    public void runtimeCopiesNestedCallerValuesBeforePersistingEvidence() throws Exception {
        Path path = Files.createTempDirectory("engine-runtime-copy").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        service.addField("interview-session", new LinkedHashMap<String, Object>() {{
            put("name", "metadata");
            put("type", "JSON");
        }});
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("source", "caller");
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("candidateName", "嵌套隔离");
        values.put("metadata", metadata);
        RuntimeRun run = service.execute(new LinkedHashMap<String, Object>() {{
            put("modelId", "interview-session");
            put("contextId", "ctx-runtime-copy");
            put("event", "startInterview");
            put("values", values);
        }});

        metadata.put("source", "caller-mutated");
        Map<?, ?> persistedInput = (Map<?, ?>) service.run(run.getId()).getInputValues().get("metadata");
        assertEquals("caller", persistedInput.get("source"));
    }

    @Test
    public void legacyOntologyProviderBindingIsNormalizedToTheExplicitLocalProvider() throws Exception {
        Path path = Files.createTempDirectory("engine-provider-legacy").resolve("state.json");
        JsonEngineStateRepository repository = new JsonEngineStateRepository(path);
        EngineState legacy = DefaultEngineSeed.create();
        legacy.getServices().get(1).setProvider("OntologyAssembler");
        repository.save(legacy);

        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));

        assertEquals("LocalOntologyProvider", service.services().get(1).getProvider());
    }

    @Test
    public void invalidUnknownFieldPolicyIsRejectedWhenStateIsLoaded() throws Exception {
        Path path = Files.createTempDirectory("engine-invalid-policy").resolve("state.json");
        JsonEngineStateRepository repository = new JsonEngineStateRepository(path);
        EngineState invalid = DefaultEngineSeed.create();
        invalid.getModels().get(0).setUnknownFieldPolicy("SILENT_FALLBACK");
        repository.save(invalid);

        try {
            new EngineAdminService(new JsonEngineStateRepository(path));
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("invalid unknownFieldPolicy"));
            return;
        }
        throw new AssertionError("invalid unknownFieldPolicy must be rejected");
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
        assertEquals(3, service.auditEvents().get(0).getChanges().size());
        assertEquals("context.values", service.auditEvents().get(0).getChanges().get(2).getPath());

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
        Map<?, ?> root = (Map<?, ?>) ((List<?>) run.getOntologyGraph().get("objects")).get(0);
        Map<?, ?> rootAttributes = (Map<?, ?>) root.get("attributes");
        assertEquals(1, rootAttributes.get("subjectCount"));
        assertEquals("集合", rootAttributes.get("subject.s-001.title"));
        TraceSpanRecord providerSpan = run.getTrace().getSpans().get(4);
        assertEquals("provider", providerSpan.getName());
        assertEquals("OK", providerSpan.getStatus());
        assertEquals("ontology-assembler", providerSpan.getAttributes().get("serviceId"));
        assertEquals("LocalOntologyProvider", providerSpan.getAttributes().get("provider"));
        assertTrue(providerSpan.getAttributes().get("requestJson").contains("assembleOntology"));
        assertTrue(providerSpan.getAttributes().get("responseJson").contains("rootObjectId"));
        assertTrue(providerSpan.getAttributes().containsKey("durationNs"));
    }

    @Test
    public void unavailableOntologyProviderIsCapturedAndDoesNotCommitContext() throws Exception {
        Path path = Files.createTempDirectory("engine-provider-failure").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("name", "subjects");
        field.put("type", "JSON");
        service.addField("questionnaire", field);
        service.updateService("ontology-assembler", new LinkedHashMap<String, Object>() {{
            put("status", "DOWN");
        }});

        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("name", "Provider 故障");
        values.put("subjectId", "subject-001");
        values.put("subjects", Arrays.<Object>asList(new LinkedHashMap<String, Object>() {{
            put("id", "s-001"); put("title", "集合");
        }}));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("modelId", "questionnaire");
        payload.put("contextId", "ctx-provider-failure");
        payload.put("event", "publish");
        payload.put("values", values);

        RuntimeRun run = service.execute(payload);

        TraceSpanRecord providerSpan = run.getTrace().getSpans().get(4);
        assertEquals("FAILED", run.getStatus());
        assertEquals("FAILED", providerSpan.getStatus());
        assertTrue(providerSpan.getAttributes().get("error").contains("not ready"));
        assertTrue(!run.isContextCommitted());
        assertEquals(0L, service.context("ctx-provider-failure").getRevision());
    }

    @Test
    public void unboundProviderImplementationIsRejectedInsteadOfSilentlyFallingBack() throws Exception {
        Path path = Files.createTempDirectory("engine-provider-binding").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("name", "subjects");
        field.put("type", "JSON");
        service.addField("questionnaire", field);
        service.updateService("ontology-assembler", new LinkedHashMap<String, Object>() {{
            put("provider", "RemoteOntologyProvider");
        }});

        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("name", "Provider 绑定");
        values.put("subjectId", "subject-001");
        values.put("subjects", Arrays.<Object>asList(new LinkedHashMap<String, Object>() {{
            put("id", "s-001"); put("title", "集合");
        }}));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("modelId", "questionnaire");
        payload.put("contextId", "ctx-provider-binding");
        payload.put("event", "publish");
        payload.put("values", values);

        RuntimeRun run = service.execute(payload);

        TraceSpanRecord providerSpan = run.getTrace().getSpans().get(4);
        assertEquals("FAILED", providerSpan.getStatus());
        assertTrue(providerSpan.getAttributes().get("error").contains("not available in-process"));
        assertTrue(!run.isContextCommitted());
    }

    @Test
    public void ontologyRuntimeRejectsConfiguredTargetTypeMismatch() throws Exception {
        Path path = Files.createTempDirectory("engine-ontology-target").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("name", "subjects");
        field.put("type", "JSON");
        service.addField("questionnaire", field);
        service.updateOntologyRelation("questionnaire", "containsSubject", new LinkedHashMap<String, Object>() {{
            put("targetType", "Option");
        }});

        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("name", "目标类型校验");
        values.put("subjectId", "s-001");
        values.put("subjects", Arrays.<Object>asList(new LinkedHashMap<String, Object>() {{
            put("id", "s-001"); put("title", "集合");
        }}));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("modelId", "questionnaire");
        payload.put("contextId", "ctx-ontology-target");
        payload.put("event", "publish");
        payload.put("values", values);

        RuntimeRun run = service.execute(payload);

        assertEquals("FAILED", run.getStatus());
        assertEquals("ONTOLOGY_ASSEMBLY_ERROR", run.getErrorCode());
        assertTrue(run.getValidationErrors().get(0).contains("target type mismatch"));
    }

    @Test
    public void ontologyRuntimeRejectsOneToOneCardinalityOverflow() throws Exception {
        Path path = Files.createTempDirectory("engine-ontology-cardinality").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        Map<String, Object> field = new LinkedHashMap<String, Object>();
        field.put("name", "subjects");
        field.put("type", "JSON");
        service.addField("questionnaire", field);
        service.updateOntologyRelation("questionnaire", "containsSubject", new LinkedHashMap<String, Object>() {{
            put("cardinality", "1:1");
        }});

        Map<String, Object> first = new LinkedHashMap<String, Object>();
        first.put("id", "s-001");
        first.put("title", "集合");
        Map<String, Object> second = new LinkedHashMap<String, Object>();
        second.put("id", "s-002");
        second.put("title", "并发");
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("name", "基数校验");
        values.put("subjectId", "s-001");
        values.put("subjects", Arrays.<Object>asList(first, second));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("modelId", "questionnaire");
        payload.put("contextId", "ctx-ontology-cardinality");
        payload.put("event", "publish");
        payload.put("values", values);

        RuntimeRun run = service.execute(payload);

        assertEquals("FAILED", run.getStatus());
        assertEquals("ONTOLOGY_ASSEMBLY_ERROR", run.getErrorCode());
        assertTrue(run.getValidationErrors().get(0).contains("cardinality"));
    }

    @Test
    public void publicReadModelsAreDetachedFromTheMutableAggregate() throws Exception {
        Path path = Files.createTempDirectory("engine-admin-boundary").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));

        EngineModel model = service.model("questionnaire");
        model.getFields().clear();
        model.getTransitions().clear();
        service.ontologyTypes().get(0).getRelations().get(0).setTargetType("Option");
        service.services().get(1).setStatus("DOWN");

        assertEquals(3, service.model("questionnaire").getFields().size());
        assertEquals("Subject", service.ontologyTypes().get(0).getRelations().get(0).getTargetType());
        assertEquals("READY", service.services().get(1).getStatus());

        RuntimeRun run = service.execute(new LinkedHashMap<String, Object>() {{
            put("modelId", "interview-session");
            put("contextId", "ctx-detached");
            put("event", "startInterview");
        }});
        run.getValues().put("notPersisted", true);
        run.getTrace().getSpans().clear();
        assertEquals(false, service.run(run.getId()).getValues().containsKey("notPersisted"));
        assertEquals(7, service.run(run.getId()).getTrace().getSpans().size());
    }

    @Test
    public void newlyRegisteredModelStartsWithCompleteVersionHistory() throws Exception {
        Path path = Files.createTempDirectory("engine-admin-model-history").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));

        service.addModel(new LinkedHashMap<String, Object>() {{
            put("id", "history-model");
            put("name", "历史模型");
            put("initialState", "DRAFT");
        }});

        EngineModel model = service.model("history-model");
        assertEquals(1, model.getSchemaVersions().size());
        assertEquals(1, model.getSchemaVersions().get(0).getVersion());
        assertEquals(1, model.getWorkflowVersions().size());
        assertEquals(1, model.getWorkflowVersions().get(0).getVersion());
    }

    @Test
    public void invalidFieldDefaultIsRejectedBeforeSchemaMutation() throws Exception {
        Path path = Files.createTempDirectory("engine-admin-default").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        try {
            service.addField("questionnaire", new LinkedHashMap<String, Object>() {{
                put("name", "invalidDefault");
                put("type", "INTEGER");
                put("defaultValue", "not-an-integer");
            }});
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("default value"));
            assertEquals(1, service.model("questionnaire").getSchemaVersion());
            assertEquals(3, service.model("questionnaire").getFields().size());
            return;
        }
        throw new AssertionError("invalid field default must be rejected");
    }

    @Test
    public void auditEventsCarryTheStateRevisionTheyBelongTo() throws Exception {
        Path path = Files.createTempDirectory("engine-admin-audit-revision").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));

        service.addModel(new LinkedHashMap<String, Object>() {{
            put("id", "audit-model");
            put("name", "审计模型");
        }});
        AuditEventRecord first = service.auditEvents().get(0);
        service.addField("audit-model", new LinkedHashMap<String, Object>() {{
            put("name", "score");
            put("type", "INTEGER");
        }});
        AuditEventRecord second = service.auditEvents().get(0);

        assertEquals(first.getAfterRevision(), second.getBeforeRevision());
        assertEquals(1L, first.getBeforeRevision());
        assertEquals(2L, first.getAfterRevision());
        assertEquals(3L, second.getAfterRevision());
        assertEquals(second.getAfterRevision(), service.revision());
        assertEquals(2, second.getChanges().size());
        assertEquals("schema.version", second.getChanges().get(0).getPath());
        assertEquals(1, second.getChanges().get(0).getBeforeValue());
        assertEquals(2, second.getChanges().get(0).getAfterValue());
        assertEquals("schema.fields[score]", second.getChanges().get(1).getPath());
        assertTrue(((Map<?, ?>) second.getChanges().get(1).getAfterValue()).containsKey("defaultValue"));
    }

    @Test
    public void ontologyAndProviderChangesUseAuditedCommands() throws Exception {
        Path path = Files.createTempDirectory("engine-admin-updates").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));

        OntologyRelationConfig relation = service.updateOntologyRelation("questionnaire", "containsSubject",
                new LinkedHashMap<String, Object>() {{ put("cardinality", "1:1"); }});
        ServiceRegistration provider = service.updateService("ontology-assembler",
                new LinkedHashMap<String, Object>() {{ put("status", "DOWN"); }});

        assertEquals("1:1", relation.getCardinality());
        assertEquals("DOWN", provider.getStatus());
        assertEquals("1:1", service.ontologyTypes().get(0).getRelations().get(0).getCardinality());
        assertEquals("DOWN", service.services().get(1).getStatus());
        assertEquals("SERVICE_UPDATED", service.auditEvents().get(0).getAction());
        assertEquals("ONTOLOGY_RELATION_UPDATED", service.auditEvents().get(1).getAction());
        assertEquals("service[ontology-assembler].status", service.auditEvents().get(0).getChanges().get(0).getPath());
        assertEquals("READY", service.auditEvents().get(0).getChanges().get(0).getBeforeValue());
        assertEquals("DOWN", service.auditEvents().get(0).getChanges().get(0).getAfterValue());
        assertEquals("ontology[questionnaire].relations[containsSubject].cardinality",
                service.auditEvents().get(1).getChanges().get(0).getPath());
    }

    @Test
    public void repeatingAnIdenticalConfigurationCommandIsIdempotent() throws Exception {
        Path path = Files.createTempDirectory("engine-admin-noop").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        long revision = service.revision();

        service.updateOntologyRelation("questionnaire", "containsSubject", new LinkedHashMap<String, Object>() {{
            put("targetType", "Subject");
            put("cardinality", "1:N");
        }});
        service.updateService("ontology-assembler", new LinkedHashMap<String, Object>() {{
            put("status", "READY");
        }});

        assertEquals(revision, service.revision());
        assertTrue(service.auditEvents().isEmpty());
    }

    private static List<String> spanNames(RuntimeRun run) {
        List<String> names = new ArrayList<String>();
        for (TraceSpanRecord span : run.getTrace().getSpans()) {
            names.add(span.getName());
        }
        return names;
    }
}
