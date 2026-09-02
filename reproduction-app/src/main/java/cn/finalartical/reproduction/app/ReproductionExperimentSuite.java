package cn.finalartical.reproduction.app;

import cn.finalartical.reproduction.admin.EngineAdminService;
import cn.finalartical.reproduction.admin.RuntimeRun;
import cn.finalartical.reproduction.experiment.ContractExperimentRunner;
import cn.finalartical.reproduction.experiment.ExperimentRunReport;
import cn.finalartical.reproduction.persistence.SqliteEngineStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reproducible mechanism, fault, and repeatability experiments for the thesis. */
public final class ReproductionExperimentSuite {
    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, Object> run(Path contractCases, Path output) throws Exception {
        Files.createDirectories(output);
        Map<String, Object> report = new LinkedHashMap<String, Object>();
        report.put("suite_id", "reproduction-abc");
        report.put("generated_at", Instant.now().toString());
        report.put("data_identity", "ENGINE_EXPERIMENT_RESULT");
        report.put("source_revision", System.getProperty("reproduction.source.revision", "UNKNOWN"));
        report.put("java_version", System.getProperty("java.version"));
        report.put("repository_under_test", "SQLite + compatibility JSON envelope");
        report.put("A_mechanism_control", mechanismControl(output.resolve("A")));
        report.put("B_fault_injection", faultInjection(output.resolve("B")));
        report.put("C_repeatability_ablation", repeatabilityAndAblation(contractCases, output.resolve("C")));
        write(report, output.resolve("report.json"));
        writeCsv(report, output.resolve("summary.csv"));
        writeManifest(contractCases, output, report);
        return report;
    }

    private Map<String, Object> mechanismControl(Path output) throws Exception {
        Path database = Files.createTempDirectory("experiment-a-explicit").resolve("state.db");
        EngineAdminService service = new EngineAdminService(new SqliteEngineStateRepository(database));
        service.addModel(new LinkedHashMap<String, Object>() {{
            put("id", "assessment-session"); put("name", "Assessment session"); put("ontologyTypeId", "questionnaire");
        }});
        service.addField("assessment-session", new LinkedHashMap<String, Object>() {{
            put("name", "name"); put("type", "STRING"); put("required", true);
        }});
        service.addField("assessment-session", new LinkedHashMap<String, Object>() {{
            put("name", "subjectId"); put("type", "STRING"); put("required", true);
        }});
        service.addField("assessment-session", new LinkedHashMap<String, Object>() {{
            put("name", "subjects"); put("type", "JSON");
        }});
        service.addTransition("assessment-session", new LinkedHashMap<String, Object>() {{
            put("fromState", "DRAFT"); put("event", "publish"); put("toState", "PUBLISHED");
        }});
        RuntimeRun explicit = service.execute(questionnairePayload("assessment-session", "experiment-a"));
        String nameDerivedRoot = nameDerivedRoot("assessment-session", service.ontologyTypes());

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("hypothesis", "a model may use a different identifier from its root ontology type");
        result.put("repository", "SQLite");
        result.put("explicit_binding", "assessment-session -> questionnaire");
        result.put("explicit_status", explicit.getStatus());
        result.put("explicit_root_type", explicit.getOntologyTypeId());
        result.put("explicit_ontology_version", explicit.getOntologyVersion());
        result.put("explicit_ontology_definition_sha256", explicit.getOntologyDefinitionSha256());
        result.put("explicit_correct", "PASSED".equals(explicit.getStatus()) && "questionnaire".equals(explicit.getOntologyTypeId()));
        result.put("name_derived_baseline_root_type", nameDerivedRoot);
        result.put("name_derived_baseline_correct", "questionnaire".equals(nameDerivedRoot));
        result.put("interpretation", "the baseline is an executable identifier-derived resolver; it is not a complete competing system");
        write(result, output.resolve("result.json"));
        writeRun(explicit, output.resolve("explicit-run.json"));
        return result;
    }

    private Map<String, Object> faultInjection(Path output) throws Exception {
        Path database = Files.createTempDirectory("experiment-b-faults").resolve("state.db");
        new EngineAdminService(new SqliteEngineStateRepository(database));
        SqliteEngineStateRepository failingRepository = new SqliteEngineStateRepository(database, null,
                new SqliteEngineStateRepository.FailureInjector() {
                    @Override
                    public void after(String step) {
                        if ("runtime_projection".equals(step)) {
                            throw new IllegalStateException("injected SQLite failure after runtime projection");
                        }
                    }
                });
        EngineAdminService failingService = new EngineAdminService(failingRepository);
        int beforeRuns = failingService.runs().size();
        boolean persistenceRejected = false;
        try {
            failingService.execute(new LinkedHashMap<String, Object>() {{
                put("modelId", "interview-session"); put("contextId", "fault-persistence"); put("event", "startInterview");
                put("values", Collections.singletonMap("candidateName", "fault"));
            }});
        } catch (RuntimeException expected) {
            persistenceRejected = true;
        }
        int afterRuns = new EngineAdminService(new SqliteEngineStateRepository(database)).runs().size();

        EngineAdminService providerService = new EngineAdminService(new SqliteEngineStateRepository(database));
        providerService.addField("questionnaire", new LinkedHashMap<String, Object>() {{
            put("name", "subjects"); put("type", "JSON");
        }});
        providerService.updateService("ontology-assembler", Collections.singletonMap("status", "DOWN"));
        RuntimeRun providerFailed = providerService.execute(questionnairePayload("questionnaire", "fault-provider"));
        providerService.updateService("ontology-assembler", Collections.singletonMap("status", "READY"));
        RuntimeRun retried = providerService.retry(providerFailed.getId());

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("repository", "SQLite");
        result.put("failure_injection_point", "after_runtime_projection_before_commit");
        result.put("persistence_failure", persistenceRejected);
        result.put("persistence_failure_run_count_before", beforeRuns);
        result.put("persistence_failure_run_count_after_restart", afterRuns);
        result.put("persistence_failure_atomic", persistenceRejected && beforeRuns == afterRuns);
        result.put("provider_outage_status", providerFailed.getStatus());
        result.put("provider_outage_error", providerFailed.getErrorCode());
        result.put("retry_status_after_recovery", retried.getStatus());
        result.put("retry_of_run_id", retried.getRetryOfRunId());
        result.put("retry_recovered", "FAILED".equals(providerFailed.getStatus()) && "PASSED".equals(retried.getStatus()));
        write(result, output.resolve("result.json"));
        writeRun(providerFailed, output.resolve("provider-failed-run.json"));
        writeRun(retried, output.resolve("retry-run.json"));
        return result;
    }

    private Map<String, Object> repeatabilityAndAblation(Path contractCases, Path output) throws Exception {
        Path database = Files.createTempDirectory("experiment-c-repeat").resolve("state.db");
        EngineAdminService service = new EngineAdminService(new SqliteEngineStateRepository(database));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("modelId", "interview-session"); payload.put("contextId", "repeatable-context");
        payload.put("event", "startInterview"); payload.put("idempotencyKey", "repeatable-key");
        payload.put("values", Collections.singletonMap("candidateName", "repeatable"));
        List<String> returnedRunIds = new ArrayList<String>();
        for (int index = 0; index < 10; index++) returnedRunIds.add(service.execute(payload).getId());
        EngineAdminService restarted = new EngineAdminService(new SqliteEngineStateRepository(database));
        RuntimeRun afterRestart = restarted.execute(payload);
        boolean sameRun = true;
        for (String runId : returnedRunIds) sameRun = sameRun && returnedRunIds.get(0).equals(runId);

        List<Map<String, Object>> contractRepetitions = new ArrayList<Map<String, Object>>();
        ContractExperimentRunner contractRunner = new ContractExperimentRunner();
        ExperimentRunReport sameSeedFirst = contractRunner.runFromCsv(contractCases, 20260902L);
        ExperimentRunReport sameSeedSecond = contractRunner.runFromCsv(contractCases, 20260902L);
        for (long seed : Arrays.asList(20260902L, 20260903L, 20260904L)) {
            ExperimentRunReport contract = contractRunner.runFromCsv(contractCases, seed);
            Path seedOutput = output.resolve("contract-seeds").resolve("seed-" + seed);
            contractRunner.writeArtifacts(contract, seedOutput);
            Map<String, Object> repetition = new LinkedHashMap<String, Object>();
            repetition.put("seed", seed); repetition.put("total", contract.getTotal());
            repetition.put("passed", contract.getPassed()); repetition.put("failed", contract.getFailed());
            repetition.put("execution_order", caseIds(contract)); contractRepetitions.add(repetition);
        }
        boolean stableContractOutcome = true;
        for (Map<String, Object> repetition : contractRepetitions) {
            stableContractOutcome = stableContractOutcome && Integer.valueOf(20).equals(repetition.get("total"))
                    && Integer.valueOf(20).equals(repetition.get("passed"));
        }

        Path ablationDatabase = Files.createTempDirectory("experiment-c-ablation").resolve("state.db");
        EngineAdminService unbound = new EngineAdminService(new SqliteEngineStateRepository(ablationDatabase));
        unbound.addModel(new LinkedHashMap<String, Object>() {{
            put("id", "assessment-unbound"); put("name", "Assessment unbound"); put("initialState", "DRAFT");
        }});
        unbound.addField("assessment-unbound", new LinkedHashMap<String, Object>() {{ put("name", "name"); put("type", "STRING"); put("required", true); }});
        unbound.addField("assessment-unbound", new LinkedHashMap<String, Object>() {{ put("name", "subjectId"); put("type", "STRING"); put("required", true); }});
        unbound.addField("assessment-unbound", new LinkedHashMap<String, Object>() {{ put("name", "subjects"); put("type", "JSON"); }});
        unbound.addTransition("assessment-unbound", new LinkedHashMap<String, Object>() {{
            put("fromState", "DRAFT"); put("event", "publish"); put("toState", "PUBLISHED");
        }});
        RuntimeRun ablationRun = unbound.execute(questionnairePayload("assessment-unbound", "ablation"));

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("repository", "SQLite"); result.put("idempotent_repetitions", 10);
        result.put("idempotent_unique_persisted_runs_after_restart", restarted.runs().size());
        boolean restartPreservedIdempotency = sameRun && afterRestart.getId().equals(returnedRunIds.get(0));
        result.put("idempotent_same_returned_run", restartPreservedIdempotency);
        result.put("restart_preserved_idempotency", restartPreservedIdempotency);
        result.put("contract_repetitions", contractRepetitions);
        result.put("contract_outcome_stable_across_seeds", stableContractOutcome);
        result.put("same_seed_report_stable", sameSeedFirst.toJson().equals(sameSeedSecond.toJson()));
        result.put("ablation", Collections.singletonMap("without_explicit_binding", new LinkedHashMap<String, Object>() {{
            put("status", ablationRun.getStatus()); put("error_code", ablationRun.getErrorCode());
            put("rejected", "FAILED".equals(ablationRun.getStatus()) && "ONTOLOGY_ASSEMBLY_ERROR".equals(ablationRun.getErrorCode()));
        }}));
        write(result, output.resolve("result.json"));
        writeRun(afterRestart, output.resolve("idempotency-after-restart.json"));
        writeRun(ablationRun, output.resolve("ablation-run.json"));
        return result;
    }

    private String nameDerivedRoot(String modelId, List<cn.finalartical.reproduction.admin.OntologyTypeConfig> types) {
        for (cn.finalartical.reproduction.admin.OntologyTypeConfig type : types) if (modelId.equals(type.getId())) return type.getId();
        return modelId;
    }

    private List<String> caseIds(ExperimentRunReport report) {
        List<String> ids = new ArrayList<String>();
        for (cn.finalartical.reproduction.experiment.ContractExecution execution : report.getExecutions()) ids.add(execution.getCaseId());
        return ids;
    }

    private Map<String, Object> questionnairePayload(String modelId, String contextId) {
        Map<String, Object> subject = new LinkedHashMap<String, Object>();
        subject.put("id", "s-001"); subject.put("title", "集合");
        subject.put("options", Arrays.<Object>asList(new LinkedHashMap<String, Object>() {{ put("id", "o-001"); put("label", "List"); }}));
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("name", "实验问卷"); values.put("subjectId", "s-001"); values.put("subjects", Arrays.<Object>asList(subject));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("modelId", modelId); payload.put("contextId", contextId); payload.put("event", "publish"); payload.put("values", values);
        return payload;
    }

    private void writeManifest(Path contractCases, Path output, Map<String, Object> report) throws IOException {
        Map<String, Object> manifest = new LinkedHashMap<String, Object>();
        manifest.put("suite_id", report.get("suite_id")); manifest.put("data_identity", report.get("data_identity"));
        manifest.put("source_revision", report.get("source_revision")); manifest.put("java_version", report.get("java_version"));
        manifest.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        manifest.put("contract_case_file", contractCases.toAbsolutePath().toString()); manifest.put("contract_case_count", 20);
        manifest.put("seeds", Arrays.asList(20260902L, 20260903L, 20260904L));
        manifest.put("rebuild_command", "mvn -q package; java -jar reproduction-app/target/reproduction-app-0.1.0-SNAPSHOT.jar experiments");
        write(manifest, output.resolve("manifest.json"));
    }

    @SuppressWarnings("unchecked")
    private void writeRun(RuntimeRun run, Path output) throws IOException { write(mapper.convertValue(run, Map.class), output); }

    private void write(Map<String, Object> value, Path output) throws IOException {
        Path parent = output.getParent(); if (parent != null) Files.createDirectories(parent);
        Files.write(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
    }

    @SuppressWarnings("unchecked")
    private void writeCsv(Map<String, Object> report, Path output) throws IOException {
        StringBuilder csv = new StringBuilder("experiment,metric,value\n");
        appendCsv(csv, "A", "explicit_correct", value(report, "A_mechanism_control", "explicit_correct"));
        appendCsv(csv, "A", "name_derived_baseline_correct", value(report, "A_mechanism_control", "name_derived_baseline_correct"));
        appendCsv(csv, "B", "persistence_failure_atomic", value(report, "B_fault_injection", "persistence_failure_atomic"));
        appendCsv(csv, "B", "retry_recovered", value(report, "B_fault_injection", "retry_recovered"));
        appendCsv(csv, "C", "idempotent_unique_persisted_runs_after_restart", value(report, "C_repeatability_ablation", "idempotent_unique_persisted_runs_after_restart"));
        appendCsv(csv, "C", "restart_preserved_idempotency", value(report, "C_repeatability_ablation", "restart_preserved_idempotency"));
        appendCsv(csv, "C", "contract_outcome_stable_across_seeds", value(report, "C_repeatability_ablation", "contract_outcome_stable_across_seeds"));
        appendCsv(csv, "C", "same_seed_report_stable", value(report, "C_repeatability_ablation", "same_seed_report_stable"));
        Files.write(output, csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private Object value(Map<String, Object> report, String section, String key) { return ((Map<String, Object>) report.get(section)).get(key); }

    private void appendCsv(StringBuilder csv, String experiment, String metric, Object value) {
        csv.append(experiment).append(',').append(metric).append(',').append(String.valueOf(value)).append('\n');
    }
}
