package cn.finalartical.reproduction.experiment;

import cn.finalartical.reproduction.compatibility.InMemoryQuestionnaireRepository;
import cn.finalartical.reproduction.compatibility.JsfExAssessService;
import cn.finalartical.reproduction.compatibility.OperationResult;
import cn.finalartical.reproduction.compatibility.OperationStatus;
import cn.finalartical.reproduction.compatibility.QuestionnaireServiceProvider;
import cn.finalartical.reproduction.ontology.OntologyAssembler;
import cn.finalartical.reproduction.ontology.Option;
import cn.finalartical.reproduction.ontology.Questionnaire;
import cn.finalartical.reproduction.ontology.Subject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ContractExperimentRunner {
    public ExperimentRunReport run(List<ContractCase> cases, long seed) {
        JsfExAssessService service = createService();
        List<ContractExecution> executions = new ArrayList<ContractExecution>();
        for (ContractCase contractCase : cases) {
            executions.add(execute(contractCase, service));
        }
        return new ExperimentRunReport("contract-20", seed, executions);
    }

    public ExperimentRunReport runFromCsv(Path caseFile, long seed) throws IOException {
        return run(new ContractCsvLoader().load(caseFile), seed);
    }

    public void writeJson(ExperimentRunReport report, Path output) throws IOException {
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(output, report.toJson().getBytes(StandardCharsets.UTF_8));
    }

    public void writeArtifacts(ExperimentRunReport report, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        writeJson(report, outputDirectory.resolve("report.json"));
        String manifest = "{"
                + "\"experiment_id\":\"contract-20\","
                + "\"run_id\":" + quote(report.getRunId()) + ","
                + "\"seed\":" + report.getSeed() + ","
                + "\"data_identity\":" + quote(ExperimentRunReport.DATA_IDENTITY) + ","
                + "\"source_revision\":" + quote(System.getProperty("reproduction.source.revision", "UNKNOWN")) + ","
                + "\"java_version\":" + quote(System.getProperty("java.version")) + ","
                + "\"os\":" + quote(System.getProperty("os.name") + " " + System.getProperty("os.version"))
                + "}";
        Files.write(outputDirectory.resolve("manifest.json"), manifest.getBytes(StandardCharsets.UTF_8));
        for (ContractExecution execution : report.getExecutions()) {
            Path caseDirectory = outputDirectory.resolve(execution.getCaseId());
            Files.createDirectories(caseDirectory);
            Files.write(caseDirectory.resolve("request.json"), execution.getRequestJson().getBytes(StandardCharsets.UTF_8));
            Files.write(caseDirectory.resolve("response.json"), execution.getResponseJson().getBytes(StandardCharsets.UTF_8));
            Files.write(caseDirectory.resolve("trace.json"), execution.getTraceJson().getBytes(StandardCharsets.UTF_8));
            Files.write(caseDirectory.resolve("result.json"), execution.getResultJson().getBytes(StandardCharsets.UTF_8));
            String hashes = "{"
                    + "\"request_sha256\":" + quote(Hashing.sha256(execution.getRequestJson())) + ","
                    + "\"response_sha256\":" + quote(Hashing.sha256(execution.getResponseJson())) + ","
                    + "\"trace_sha256\":" + quote(Hashing.sha256(execution.getTraceJson())) + ","
                    + "\"result_sha256\":" + quote(Hashing.sha256(execution.getResultJson()))
                    + "}";
            Files.write(caseDirectory.resolve("sha256.json"), hashes.getBytes(StandardCharsets.UTF_8));
        }
    }

    private ContractExecution execute(ContractCase contractCase, JsfExAssessService service) {
        String traceId = "trace-" + contractCase.getCaseId();
        OperationResult<?> result;
        String scenario = contractCase.getScenario();
        String capability = contractCase.getCapability();

        if ("questionnaire-query".equals(capability) || "subject-questionnaire-query".equals(capability)) {
            result = service.queryQuestionnaireIdsBySubjectId(inputFor(scenario), traceId);
        } else if ("linkage-config-query".equals(capability)) {
            result = service.queryQuestionnaireLinkageConfig(inputForQuestionnaire(scenario), traceId);
        } else if ("linkage-config-save".equals(capability)) {
            result = service.saveQuestionnaireLinkageConfig(inputForQuestionnaire(scenario), "v1", traceId);
        } else if ("interview-session-detail".equals(capability)) {
            result = service.questionnaireDetail(inputForQuestionnaire(scenario), traceId);
        } else {
            result = JsfExAssessService.providerUnavailable(traceId);
        }

        String rawStatus = result.getStatus().name();
        String actualBehavior = compatibilityBehavior(scenario, rawStatus);
        boolean passed = matches(contractCase.getExpectedBehavior(), scenario, rawStatus);
        String requestJson = "{"
                + "\"trace_id\":" + quote(traceId)
                + ",\"capability\":" + quote(capability)
                + ",\"scenario\":" + quote(scenario)
                + ",\"request_shape\":" + quote(contractCase.getRequestShape())
                + "}";
        String responseJson = "{"
                + "\"trace_id\":" + quote(traceId)
                + ",\"status\":" + quote(rawStatus)
                + ",\"message\":" + quote(result.getMessage())
                + ",\"has_data\":" + (result.getData() != null)
                + ",\"data\":" + dataJson(result.getData())
                + "}";
        String traceJson = "{"
                + "\"trace_id\":" + quote(traceId)
                + ",\"spans\":["
                + "{\"name\":\"consumer\",\"status\":\"OK\"},"
                + "{\"name\":\"provider\",\"status\":" + quote(rawStatus) + "},"
                + "{\"name\":\"response\",\"status\":" + quote(rawStatus) + "}]"
                + "}";
        return new ContractExecution(contractCase.getCaseId(), capability, scenario,
                contractCase.getRequestShape(), contractCase.getExpectedBehavior(), rawStatus,
                actualBehavior, traceId, passed, ExperimentRunReport.DATA_IDENTITY,
                requestJson, responseJson, traceJson);
    }

    private static JsfExAssessService createService() {
        InMemoryQuestionnaireRepository repository = new InMemoryQuestionnaireRepository()
                .add(new Questionnaire("q-001", "Java 面试基础", "subject-001")
                        .addSubject(new Subject("s-001", "集合").addOption(new Option("o-001", "List"))))
                .add(new Questionnaire("q-002", "Java 并发", "subject-001"));
        repository.saveLinkageConfig(new cn.finalartical.reproduction.compatibility.QuestionnaireLinkageConfig("q-001", "v1"));
        return new JsfExAssessService(new QuestionnaireServiceProvider(repository, new OntologyAssembler()));
    }

    private static String inputFor(String scenario) {
        if ("null".equals(scenario)) {
            return null;
        }
        if ("invalid".equals(scenario)) {
            return "!invalid";
        }
        return "subject-001";
    }

    private static String inputForQuestionnaire(String scenario) {
        if ("null".equals(scenario)) {
            return null;
        }
        if ("invalid".equals(scenario)) {
            return "!invalid";
        }
        return "q-001";
    }

    private static String compatibilityBehavior(String scenario, String rawStatus) {
        if ("compatibility".equals(scenario) && OperationStatus.SUCCESS.name().equals(rawStatus)) {
            return OperationStatus.COMPATIBLE_SUCCESS.name();
        }
        return rawStatus;
    }

    private static boolean matches(String expected, String scenario, String rawStatus) {
        if ("EMPTY_OR_ALL_BY_POLICY".equals(expected)) {
            return OperationStatus.SUCCESS.name().equals(rawStatus) || OperationStatus.EMPTY.name().equals(rawStatus);
        }
        if ("COMPATIBLE_SUCCESS".equals(expected)) {
            return "compatibility".equals(scenario) && OperationStatus.SUCCESS.name().equals(rawStatus);
        }
        return expected.equals(rawStatus);
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String dataJson(Object data) {
        if (data == null) {
            return "null";
        }
        if (data instanceof List) {
            StringBuilder builder = new StringBuilder("[");
            List<?> values = (List<?>) data;
            for (int index = 0; index < values.size(); index++) {
                if (index > 0) {
                    builder.append(',');
                }
                builder.append(quote(String.valueOf(values.get(index))));
            }
            return builder.append(']').toString();
        }
        if (data instanceof cn.finalartical.reproduction.compatibility.QuestionnaireLinkageConfig) {
            cn.finalartical.reproduction.compatibility.QuestionnaireLinkageConfig config =
                    (cn.finalartical.reproduction.compatibility.QuestionnaireLinkageConfig) data;
            return "{\"questionnaire_id\":" + quote(config.getQuestionnaireId())
                    + ",\"version\":" + quote(config.getVersion()) + "}";
        }
        if (data instanceof cn.finalartical.reproduction.ontology.JobOntologyDetail) {
            cn.finalartical.reproduction.ontology.JobOntologyDetail detail =
                    (cn.finalartical.reproduction.ontology.JobOntologyDetail) data;
            StringBuilder builder = new StringBuilder("{\"object_type\":")
                    .append(quote(detail.getObjectType()))
                    .append(",\"object_id\":").append(quote(detail.getObjectId()))
                    .append(",\"source_version\":").append(detail.getSourceVersion())
                    .append(",\"fixed_attributes\":").append(mapJson(detail.getFixedAttributes()))
                    .append(",\"dynamic_attributes\":").append(mapJson(detail.getDynamicAttributes()))
                    .append(",\"relation_count\":").append(detail.getRelations().size())
                    .append('}');
            return builder.toString();
        }
        return quote(String.valueOf(data));
    }

    private static String mapJson(java.util.Map<String, Object> values) {
        StringBuilder builder = new StringBuilder("{");
        int index = 0;
        for (java.util.Map.Entry<String, Object> entry : values.entrySet()) {
            if (index++ > 0) {
                builder.append(',');
            }
            builder.append(quote(entry.getKey())).append(':');
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                builder.append(String.valueOf(value));
            } else {
                builder.append(quote(String.valueOf(value)));
            }
        }
        return builder.append('}').toString();
    }
}
