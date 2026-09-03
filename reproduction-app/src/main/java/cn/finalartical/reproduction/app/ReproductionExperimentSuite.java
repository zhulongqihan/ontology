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
        report.put("suite_id", "reproduction-abcd");
        report.put("generated_at", Instant.now().toString());
        report.put("data_identity", "ENGINE_EXPERIMENT_RESULT");
        report.put("source_revision", System.getProperty("reproduction.source.revision", "UNKNOWN"));
        report.put("java_version", System.getProperty("java.version"));
        report.put("repository_under_test", "SQLite + compatibility JSON envelope");
        report.put("A_mechanism_control", mechanismControl(output.resolve("A")));
        report.put("B_fault_injection", faultInjection(output.resolve("B")));
        report.put("C_repeatability_ablation", repeatabilityAndAblation(contractCases, output.resolve("C")));
        report.put("D_baseline_flexible_comparison", baselineFlexibleComparison(output.resolve("D")));
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

    private Map<String, Object> baselineFlexibleComparison(Path output) throws Exception {
        Files.createDirectories(output);
        List<String> caseIds = Arrays.asList(
                "questionnaire-basic",
                "questionnaire-dynamic-field",
                "questionnaire-knowledge-graph");
        Map<String, Object> cases = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> allObservations = new ArrayList<Map<String, Object>>();
        Map<String, Object> runtimeGraphEvidence = null;
        for (String caseId : caseIds) {
            List<Map<String, Object>> observations = new ArrayList<Map<String, Object>>();
            for (int trial = 1; trial <= 12; trial++) {
                Path database = Files.createTempDirectory("experiment-d-" + caseId + "-" + trial).resolve("state.db");
                EngineAdminService service = new EngineAdminService(new SqliteEngineStateRepository(database));
                configureComparisonCase(service, caseId);
                Map<String, Object> payload = comparisonPayload(caseId, "cmp-" + caseId + "-" + trial);
                Map<String, Object> comparison = service.executeComparison(payload);
                RuntimeRun baseline = (RuntimeRun) comparison.get("baselineRun");
                RuntimeRun flexible = (RuntimeRun) comparison.get("flexibleRun");
                Map<String, Object> observation = new LinkedHashMap<String, Object>();
                observation.put("trial", trial);
                observation.put("comparison_id", comparison.get("comparisonId"));
                observation.put("case_id", caseId);
                observation.put("comparable", comparison.get("comparable"));
                observation.put("input_sha256", baseline.getInputSha256());
                observation.put("baseline_run_id", baseline.getId());
                observation.put("flexible_run_id", flexible.getId());
                observation.put("baseline_status", baseline.getStatus());
                observation.put("flexible_status", flexible.getStatus());
                observation.put("baseline_error_code", baseline.getErrorCode());
                observation.put("flexible_error_code", flexible.getErrorCode());
                observation.put("baseline_duration_ns", baseline.getDurationNs());
                observation.put("flexible_duration_ns", flexible.getDurationNs());
                observation.put("duration_delta_ns", flexible.getDurationNs() - baseline.getDurationNs());
                observation.put("outcome_improved", "FAILED".equals(baseline.getStatus())
                        && "PASSED".equals(flexible.getStatus()));
                observation.put("baseline_configuration_sha256", baseline.getConfigurationSha256());
                observation.put("flexible_configuration_sha256", flexible.getConfigurationSha256());
                observations.add(observation);
                allObservations.add(observation);
                if ("questionnaire-knowledge-graph".equals(caseId) && runtimeGraphEvidence == null) {
                    runtimeGraphEvidence = flexible.getOntologyGraph();
                }
            }
            Map<String, Object> summary = summarizeComparisonCase(caseId, observations);
            cases.put(caseId, summary);
            write(observations, output.resolve(caseId + "-observations.json"));
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("protocol", "每个案例 12 次独立 SQLite 初始化；每次先执行固定字段基线，再执行同输入的 Flexible Engine；不使用原生产服务");
        result.put("baseline_identity", "RigidMappingBaseline v1");
        result.put("flexible_identity", "FlexibleEngine + configured schema/workflow/ontology");
        result.put("comparison_input_rule", "input_sha256 必须相同，configuration_sha256 必须不同");
        result.put("case_count", cases.size());
        result.put("trial_count_per_case", 12);
        result.put("cases", cases);
        result.put("observations", allObservations);
        result.put("interpretation", "这些是本地可复现实现之间的成对工程观察；不能外推为原生产系统等价性或业务指标提升");
        write(result, output.resolve("result.json"));
        writeDurationSvg(cases, output.resolve("duration-comparison.svg"));
        if (runtimeGraphEvidence != null) {
            write(runtimeGraphEvidence, output.resolve("knowledge-graph-runtime.json"));
            writeKnowledgeGraphSvg(runtimeGraphEvidence, output.resolve("knowledge-graph-runtime.svg"));
        }
        return result;
    }

    private void configureComparisonCase(EngineAdminService service, String caseId) {
        if ("questionnaire-dynamic-field".equals(caseId)) {
            service.addField("questionnaire", new LinkedHashMap<String, Object>() {{
                put("name", "reviewerNote"); put("type", "STRING"); put("required", false);
            }});
        }
        if ("questionnaire-knowledge-graph".equals(caseId)) {
            service.addField("questionnaire", new LinkedHashMap<String, Object>() {{
                put("name", "subjects"); put("type", "JSON"); put("required", false);
            }});
        }
    }

    private Map<String, Object> comparisonPayload(String caseId, String comparisonId) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("name", "实验问卷-" + caseId);
        values.put("subjectId", "s-001");
        if ("questionnaire-dynamic-field".equals(caseId)) {
            values.put("reviewerNote", "本次评审新增字段");
        }
        if ("questionnaire-knowledge-graph".equals(caseId)) {
            Map<String, Object> option = new LinkedHashMap<String, Object>();
            option.put("id", "o-001"); option.put("label", "List");
            Map<String, Object> subject = new LinkedHashMap<String, Object>();
            subject.put("id", "s-001"); subject.put("title", "集合");
            subject.put("options", Arrays.<Object>asList(option));
            values.put("subjects", Arrays.<Object>asList(subject));
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("comparisonId", comparisonId);
        payload.put("caseId", caseId);
        payload.put("modelId", "questionnaire");
        payload.put("event", "publish");
        payload.put("values", values);
        return payload;
    }

    private Map<String, Object> summarizeComparisonCase(String caseId, List<Map<String, Object>> observations) {
        List<Long> baselineDurations = new ArrayList<Long>();
        List<Long> flexibleDurations = new ArrayList<Long>();
        int comparable = 0;
        int baselinePassed = 0;
        int flexiblePassed = 0;
        int outcomeImproved = 0;
        boolean inputHashesConsistent = true;
        boolean configurationHashesDistinct = true;
        boolean baselineConfigurationsConsistent = true;
        boolean flexibleConfigurationsConsistent = true;
        String firstInputHash = null;
        String firstBaselineConfigurationHash = null;
        String firstFlexibleConfigurationHash = null;
        for (Map<String, Object> observation : observations) {
            baselineDurations.add(((Number) observation.get("baseline_duration_ns")).longValue());
            flexibleDurations.add(((Number) observation.get("flexible_duration_ns")).longValue());
            if (Boolean.TRUE.equals(observation.get("comparable"))) comparable++;
            if ("PASSED".equals(observation.get("baseline_status"))) baselinePassed++;
            if ("PASSED".equals(observation.get("flexible_status"))) flexiblePassed++;
            if (Boolean.TRUE.equals(observation.get("outcome_improved"))) outcomeImproved++;
            String inputHash = String.valueOf(observation.get("input_sha256"));
            if (firstInputHash == null) firstInputHash = inputHash;
            inputHashesConsistent = inputHashesConsistent && firstInputHash.equals(inputHash);
            configurationHashesDistinct = configurationHashesDistinct
                    && !String.valueOf(observation.get("baseline_configuration_sha256"))
                    .equals(String.valueOf(observation.get("flexible_configuration_sha256")));
            String baselineConfigurationHash = String.valueOf(observation.get("baseline_configuration_sha256"));
            String flexibleConfigurationHash = String.valueOf(observation.get("flexible_configuration_sha256"));
            if (firstBaselineConfigurationHash == null) firstBaselineConfigurationHash = baselineConfigurationHash;
            if (firstFlexibleConfigurationHash == null) firstFlexibleConfigurationHash = flexibleConfigurationHash;
            baselineConfigurationsConsistent = baselineConfigurationsConsistent
                    && firstBaselineConfigurationHash.equals(baselineConfigurationHash);
            flexibleConfigurationsConsistent = flexibleConfigurationsConsistent
                    && firstFlexibleConfigurationHash.equals(flexibleConfigurationHash);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("case_id", caseId);
        result.put("observations", observations.size());
        result.put("comparable_pairs", comparable);
        result.put("baseline_passed", baselinePassed);
        result.put("flexible_passed", flexiblePassed);
        result.put("outcome_improved_pairs", outcomeImproved);
        result.put("input_hashes_consistent", inputHashesConsistent);
        result.put("configuration_hashes_distinct", configurationHashesDistinct);
        result.put("baseline_configuration_hash_consistent", baselineConfigurationsConsistent);
        result.put("flexible_configuration_hash_consistent", flexibleConfigurationsConsistent);
        result.put("baseline_duration_ns", durationSummary(baselineDurations));
        result.put("flexible_duration_ns", durationSummary(flexibleDurations));
        result.put("duration_delta_ns", durationSummary(deltas(baselineDurations, flexibleDurations)));
        return result;
    }

    private List<Long> deltas(List<Long> baseline, List<Long> flexible) {
        List<Long> result = new ArrayList<Long>();
        for (int index = 0; index < baseline.size(); index++) result.add(flexible.get(index) - baseline.get(index));
        return result;
    }

    private Map<String, Object> durationSummary(List<Long> values) {
        List<Long> sorted = new ArrayList<Long>(values);
        Collections.sort(sorted);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("min", sorted.get(0));
        result.put("p50", percentile(sorted, 0.50));
        result.put("p95", percentile(sorted, 0.95));
        result.put("max", sorted.get(sorted.size() - 1));
        return result;
    }

    private long percentile(List<Long> sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    @SuppressWarnings("unchecked")
    private void writeDurationSvg(Map<String, Object> cases, Path output) throws IOException {
        int width = 920;
        int rowHeight = 88;
        int height = 90 + cases.size() * rowHeight;
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width)
                .append("\" height=\"").append(height).append("\" viewBox=\"0 0 ")
                .append(width).append(' ').append(height).append("\">");
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#101827\"/>");
        svg.append("<text x=\"32\" y=\"42\" fill=\"#f5f7fb\" font-size=\"24\" font-family=\"Arial\">固定映射基线 vs Flexible Engine：p50 原始耗时（ns）</text>");
        int row = 0;
        for (Map.Entry<String, Object> entry : cases.entrySet()) {
            Map<String, Object> summary = (Map<String, Object>) entry.getValue();
            Map<String, Object> baseline = (Map<String, Object>) summary.get("baseline_duration_ns");
            Map<String, Object> flexible = (Map<String, Object>) summary.get("flexible_duration_ns");
            long baselineValue = ((Number) baseline.get("p50")).longValue();
            long flexibleValue = ((Number) flexible.get("p50")).longValue();
            long scale = Math.max(1L, Math.max(baselineValue, flexibleValue));
            int y = 76 + row * rowHeight;
            svg.append("<text x=\"32\" y=\"").append(y + 18).append("\" fill=\"#b9c5d6\" font-size=\"15\" font-family=\"Arial\">")
                    .append(escapeXml(entry.getKey())).append("</text>");
            svg.append("<rect x=\"260\" y=\"").append(y).append("\" width=\"").append(Math.max(4, 540 * baselineValue / scale))
                    .append("\" height=\"20\" rx=\"4\" fill=\"#e6a35c\"/>");
            svg.append("<rect x=\"260\" y=\"").append(y + 28).append("\" width=\"").append(Math.max(4, 540 * flexibleValue / scale))
                    .append("\" height=\"20\" rx=\"4\" fill=\"#63d7c6\"/>");
            svg.append("<text x=\"810\" y=\"").append(y + 16).append("\" fill=\"#e6a35c\" font-size=\"14\" font-family=\"Arial\">baseline ")
                    .append(baselineValue).append(" ns</text>");
            svg.append("<text x=\"810\" y=\"").append(y + 44).append("\" fill=\"#63d7c6\" font-size=\"14\" font-family=\"Arial\">flexible ")
                    .append(flexibleValue).append(" ns</text>");
            row++;
        }
        svg.append("</svg>");
        Files.write(output, svg.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @SuppressWarnings("unchecked")
    private void writeKnowledgeGraphSvg(Map<String, Object> graph, Path output) throws IOException {
        List<Map<String, Object>> objects = (List<Map<String, Object>>) graph.get("objects");
        List<Map<String, Object>> relations = (List<Map<String, Object>>) graph.get("relations");
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"980\" height=\"310\" viewBox=\"0 0 980 310\">");
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#101827\"/>");
        svg.append("<text x=\"28\" y=\"38\" fill=\"#f5f7fb\" font-size=\"23\" font-family=\"Arial\">Flexible Engine Runtime Knowledge Graph</text>");
        Map<String, int[]> positions = new LinkedHashMap<String, int[]>();
        int index = 0;
        for (Map<String, Object> object : objects) {
            int x = 32 + index * 300;
            int y = 138 + (index % 2) * 72;
            positions.put(String.valueOf(object.get("id")), new int[] {x, y});
            index++;
        }
        for (Map<String, Object> relation : relations) {
            int[] source = positions.get(String.valueOf(relation.get("sourceId")));
            int[] target = positions.get(String.valueOf(relation.get("targetId")));
            if (source == null || target == null) continue;
            svg.append("<line x1=\"").append(source[0] + 118).append("\" y1=\"").append(source[1] + 28)
                    .append("\" x2=\"").append(target[0]).append("\" y2=\"").append(target[1] + 28)
                    .append("\" stroke=\"#63d7c6\" stroke-width=\"2\" marker-end=\"url(#arrow)\"/>");
            svg.append("<text x=\"").append((source[0] + target[0]) / 2 + 35).append("\" y=\"")
                    .append((source[1] + target[1]) / 2 + 18).append("\" fill=\"#b9c5d6\" font-size=\"13\" font-family=\"Arial\">")
                    .append(escapeXml(String.valueOf(relation.get("relation")))).append("</text>");
        }
        svg.append("<defs><marker id=\"arrow\" markerWidth=\"8\" markerHeight=\"8\" refX=\"7\" refY=\"3\" orient=\"auto\"><path d=\"M0,0 L0,6 L7,3 z\" fill=\"#63d7c6\"/></marker></defs>");
        for (Map<String, Object> object : objects) {
            int[] position = positions.get(String.valueOf(object.get("id")));
            Map<String, Object> attributes = object.get("attributes") instanceof Map
                    ? (Map<String, Object>) object.get("attributes") : Collections.<String, Object>emptyMap();
            svg.append("<rect x=\"").append(position[0]).append("\" y=\"").append(position[1])
                    .append("\" width=\"236\" height=\"56\" rx=\"8\" fill=\"#172436\" stroke=\"#63d7c6\"/>");
            svg.append("<text x=\"").append(position[0] + 14).append("\" y=\"").append(position[1] + 20)
                    .append("\" fill=\"#63d7c6\" font-size=\"12\" font-family=\"Arial\">")
                    .append(escapeXml(String.valueOf(object.get("type")))).append("</text>");
            svg.append("<text x=\"").append(position[0] + 14).append("\" y=\"").append(position[1] + 41)
                    .append("\" fill=\"#f5f7fb\" font-size=\"15\" font-family=\"Arial\">")
                    .append(escapeXml(String.valueOf(object.get("id")))).append(" · ").append(attributes.size()).append(" attrs</text>");
        }
        svg.append("</svg>");
        Files.write(output, svg.toString().getBytes(StandardCharsets.UTF_8));
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
        manifest.put("rebuild_command", "mvn -q clean package; java -jar reproduction-app/target/reproduction-app-0.1.0-SNAPSHOT.jar experiments <contract.csv> <output>");
        write(manifest, output.resolve("manifest.json"));
    }

    @SuppressWarnings("unchecked")
    private void writeRun(RuntimeRun run, Path output) throws IOException { write(mapper.convertValue(run, Map.class), output); }

    private void write(Object value, Path output) throws IOException {
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
        Map<String, Object> comparison = (Map<String, Object>) report.get("D_baseline_flexible_comparison");
        Map<String, Object> comparisonCases = (Map<String, Object>) comparison.get("cases");
        for (Map.Entry<String, Object> entry : comparisonCases.entrySet()) {
            Map<String, Object> summary = (Map<String, Object>) entry.getValue();
            appendCsv(csv, "D:" + entry.getKey(), "comparable_pairs", summary.get("comparable_pairs"));
            appendCsv(csv, "D:" + entry.getKey(), "outcome_improved_pairs", summary.get("outcome_improved_pairs"));
            appendCsv(csv, "D:" + entry.getKey(), "baseline_p50_ns",
                    ((Map<String, Object>) summary.get("baseline_duration_ns")).get("p50"));
            appendCsv(csv, "D:" + entry.getKey(), "flexible_p50_ns",
                    ((Map<String, Object>) summary.get("flexible_duration_ns")).get("p50"));
        }
        Files.write(output, csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private Object value(Map<String, Object> report, String section, String key) { return ((Map<String, Object>) report.get(section)).get(key); }

    private void appendCsv(StringBuilder csv, String experiment, String metric, Object value) {
        csv.append(experiment).append(',').append(metric).append(',').append(String.valueOf(value)).append('\n');
    }
}
