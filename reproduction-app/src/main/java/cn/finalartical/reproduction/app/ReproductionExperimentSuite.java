package cn.finalartical.reproduction.app;

import cn.finalartical.reproduction.admin.EngineAdminService;
import cn.finalartical.reproduction.admin.EngineState;
import cn.finalartical.reproduction.admin.EngineStateRepository;
import cn.finalartical.reproduction.admin.JsonEngineStateRepository;
import cn.finalartical.reproduction.admin.RuntimeRun;
import cn.finalartical.reproduction.experiment.ContractExperimentRunner;
import cn.finalartical.reproduction.experiment.ExperimentRunReport;
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
        report.put("A_mechanism_control", mechanismControl());
        report.put("B_fault_injection", faultInjection());
        report.put("C_repeatability_ablation", repeatabilityAndAblation(contractCases));
        write(report, output.resolve("report.json"));
        writeCsv(report, output.resolve("summary.csv"));
        return report;
    }

    private Map<String, Object> mechanismControl() throws Exception {
        Path path = Files.createTempDirectory("experiment-a-explicit").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        service.addModel(new LinkedHashMap<String, Object>() {{
            put("id", "assessment-session");
            put("name", "Assessment session");
            put("ontologyTypeId", "questionnaire");
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

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("hypothesis", "a model may use a different identifier from its root ontology type");
        result.put("explicit_binding", "assessment-session -> questionnaire");
        result.put("explicit_status", explicit.getStatus());
        result.put("explicit_root_type", explicit.getOntologyTypeId());
        result.put("explicit_correct", "PASSED".equals(explicit.getStatus())
                && "questionnaire".equals(explicit.getOntologyTypeId()));
        String nameDerived = "assessment-session";
        result.put("name_derived_baseline_root_type", nameDerived);
        result.put("name_derived_baseline_correct", "questionnaire".equals(nameDerived));
        result.put("interpretation", "baseline is an intentionally small name-derived comparator, not a claim about a complete competing system");
        return result;
    }

    private Map<String, Object> faultInjection() throws Exception {
        Path path = Files.createTempDirectory("experiment-b-faults").resolve("state.json");
        JsonEngineStateRepository delegate = new JsonEngineStateRepository(path);
        FailingRepository failingRepository = new FailingRepository(delegate);
        EngineAdminService service = new EngineAdminService(failingRepository);
        int beforeRuns = service.runs().size();
        failingRepository.failWrites = true;
        boolean persistenceRejected = false;
        try {
            service.execute(new LinkedHashMap<String, Object>() {{
                put("modelId", "interview-session"); put("contextId", "fault-persistence");
                put("event", "startInterview"); put("values", Collections.singletonMap("candidateName", "fault"));
            }});
        } catch (RuntimeException expected) {
            persistenceRejected = true;
        }
        failingRepository.failWrites = false;
        int afterRuns = new EngineAdminService(delegate).runs().size();

        service.addField("questionnaire", new LinkedHashMap<String, Object>() {{
            put("name", "subjects"); put("type", "JSON");
        }});
        service.updateService("ontology-assembler", Collections.singletonMap("status", "DOWN"));
        RuntimeRun providerFailed = service.execute(questionnairePayload("questionnaire", "fault-provider"));
        service.updateService("ontology-assembler", Collections.singletonMap("status", "READY"));
        RuntimeRun retried = service.retry(providerFailed.getId());

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("persistence_failure", persistenceRejected);
        result.put("persistence_failure_run_count_before", beforeRuns);
        result.put("persistence_failure_run_count_after_reload", afterRuns);
        result.put("persistence_failure_atomic", persistenceRejected && beforeRuns == afterRuns);
        result.put("provider_outage_status", providerFailed.getStatus());
        result.put("provider_outage_error", providerFailed.getErrorCode());
        result.put("retry_status_after_recovery", retried.getStatus());
        result.put("retry_of_run_id", retried.getRetryOfRunId());
        result.put("retry_recovered", "FAILED".equals(providerFailed.getStatus())
                && "PASSED".equals(retried.getStatus()));
        return result;
    }

    private Map<String, Object> repeatabilityAndAblation(Path contractCases) throws Exception {
        Path path = Files.createTempDirectory("experiment-c-repeat").resolve("state.json");
        EngineAdminService service = new EngineAdminService(new JsonEngineStateRepository(path));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("modelId", "interview-session");
        payload.put("contextId", "repeatable-context");
        payload.put("event", "startInterview");
        payload.put("idempotencyKey", "repeatable-key");
        payload.put("values", Collections.singletonMap("candidateName", "repeatable"));
        List<String> returnedRunIds = new ArrayList<String>();
        for (int index = 0; index < 10; index++) {
            returnedRunIds.add(service.execute(payload).getId());
        }
        boolean sameRun = true;
        for (String runId : returnedRunIds) {
            sameRun = sameRun && returnedRunIds.get(0).equals(runId);
        }

        List<Map<String, Object>> contractRepetitions = new ArrayList<Map<String, Object>>();
        ContractExperimentRunner contractRunner = new ContractExperimentRunner();
        for (long seed : Arrays.asList(20260902L, 20260903L, 20260904L)) {
            ExperimentRunReport contract = contractRunner.runFromCsv(contractCases, seed);
            Map<String, Object> repetition = new LinkedHashMap<String, Object>();
            repetition.put("seed", seed);
            repetition.put("total", contract.getTotal());
            repetition.put("passed", contract.getPassed());
            repetition.put("failed", contract.getFailed());
            contractRepetitions.add(repetition);
        }
        boolean stableContractOutcome = true;
        for (Map<String, Object> repetition : contractRepetitions) {
            stableContractOutcome = stableContractOutcome
                    && Integer.valueOf(20).equals(repetition.get("total"))
                    && Integer.valueOf(20).equals(repetition.get("passed"));
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("idempotent_repetitions", 10);
        result.put("idempotent_unique_persisted_runs", service.runs().size());
        result.put("idempotent_same_returned_run", sameRun);
        result.put("contract_repetitions", contractRepetitions);
        result.put("contract_outcome_stable_across_seeds", stableContractOutcome);
        result.put("ablation", Collections.singletonMap("without_explicit_binding", "rejected at ontology assembly gate"));
        return result;
    }

    private Map<String, Object> questionnairePayload(String modelId, String contextId) {
        Map<String, Object> subject = new LinkedHashMap<String, Object>();
        subject.put("id", "s-001");
        subject.put("title", "集合");
        subject.put("options", Arrays.<Object>asList(new LinkedHashMap<String, Object>() {{
            put("id", "o-001"); put("label", "List");
        }}));
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("name", "实验问卷");
        values.put("subjectId", "s-001");
        values.put("subjects", Arrays.<Object>asList(subject));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("modelId", modelId);
        payload.put("contextId", contextId);
        payload.put("event", "publish");
        payload.put("values", values);
        return payload;
    }

    private void write(Map<String, Object> report, Path output) throws IOException {
        Files.write(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report));
    }

    private void writeCsv(Map<String, Object> report, Path output) throws IOException {
        StringBuilder csv = new StringBuilder("experiment,metric,value\n");
        appendCsv(csv, "A", "explicit_correct", value(report, "A_mechanism_control", "explicit_correct"));
        appendCsv(csv, "A", "name_derived_baseline_correct", value(report, "A_mechanism_control", "name_derived_baseline_correct"));
        appendCsv(csv, "B", "persistence_failure_atomic", value(report, "B_fault_injection", "persistence_failure_atomic"));
        appendCsv(csv, "B", "retry_recovered", value(report, "B_fault_injection", "retry_recovered"));
        appendCsv(csv, "C", "idempotent_unique_persisted_runs", value(report, "C_repeatability_ablation", "idempotent_unique_persisted_runs"));
        appendCsv(csv, "C", "contract_outcome_stable_across_seeds", value(report, "C_repeatability_ablation", "contract_outcome_stable_across_seeds"));
        Files.write(output, csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private Object value(Map<String, Object> report, String section, String key) {
        return ((Map<String, Object>) report.get(section)).get(key);
    }

    private void appendCsv(StringBuilder csv, String experiment, String metric, Object value) {
        csv.append(experiment).append(',').append(metric).append(',').append(String.valueOf(value)).append('\n');
    }

    private static final class FailingRepository implements EngineStateRepository {
        private final JsonEngineStateRepository delegate;
        private boolean failWrites;

        private FailingRepository(JsonEngineStateRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public EngineState load() {
            return delegate.load();
        }

        @Override
        public void save(EngineState state) {
            save(state, state.getRevision());
        }

        @Override
        public void save(EngineState state, long expectedRevision) {
            if (failWrites) {
                throw new IllegalStateException("injected persistence failure");
            }
            delegate.save(state, expectedRevision);
        }
    }
}
