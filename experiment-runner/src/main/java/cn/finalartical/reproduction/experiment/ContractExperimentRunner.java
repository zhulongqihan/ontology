package cn.finalartical.reproduction.experiment;

import cn.finalartical.reproduction.compatibility.InMemoryQuestionnaireRepository;
import cn.finalartical.reproduction.compatibility.JsfExAssessService;
import cn.finalartical.reproduction.compatibility.OperationResult;
import cn.finalartical.reproduction.compatibility.OperationStatus;
import cn.finalartical.reproduction.compatibility.QuestionnaireServiceProvider;
import cn.finalartical.reproduction.flexible.Trace;
import cn.finalartical.reproduction.flexible.TraceSpan;
import cn.finalartical.reproduction.ontology.OntologyAssembler;
import cn.finalartical.reproduction.ontology.Option;
import cn.finalartical.reproduction.ontology.Questionnaire;
import cn.finalartical.reproduction.ontology.Subject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class ContractExperimentRunner {
    public ExperimentRunReport run(List<ContractCase> cases, long seed) {
        JsfExAssessService service = createService();
        List<ContractExecution> executions = new ArrayList<ContractExecution>();
        List<ContractCase> executionOrder = new ArrayList<ContractCase>(cases);
        Collections.shuffle(executionOrder, new Random(seed));
        for (ContractCase contractCase : executionOrder) {
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
        Trace trace = new Trace("contract-" + contractCase.getCaseId(), traceId);
        OperationResult<?> result;
        String scenario = contractCase.getScenario();
        String capability = contractCase.getCapability();
        String operation = capability;
        String providerError = null;
        long consumerStarted = System.nanoTime();
        Instant consumerStartedAt = Instant.now();
        long providerStarted = System.nanoTime();
        Instant providerStartedAt = Instant.now();

        Map<String, Object> arguments = requestArguments(contractCase);
        String requestJson = "{"
                + "\"trace_id\":" + quote(traceId)
                + ",\"capability\":" + quote(capability)
                + ",\"scenario\":" + quote(scenario)
                + ",\"request_shape\":" + quote(contractCase.getRequestShape())
                + ",\"arguments\":" + mapJson(arguments)
                + "}";
        try {
            if ("questionnaire-query".equals(capability) || "subject-questionnaire-query".equals(capability)) {
                operation = "queryQuestionnaireIdsBySubjectId";
                result = service.queryQuestionnaireIdsByRequest(arguments, traceId);
            } else if ("linkage-config-query".equals(capability)) {
                operation = "queryQuestionnaireLinkageConfig";
                result = service.queryQuestionnaireLinkageConfigByRequest(arguments, traceId);
            } else if ("linkage-config-save".equals(capability)) {
                operation = "saveQuestionnaireLinkageConfig";
                result = service.saveQuestionnaireLinkageConfigByRequest(arguments, traceId);
            } else if ("interview-session-detail".equals(capability)) {
                operation = "questionnaireDetail";
                result = service.questionnaireDetailByRequest(arguments, traceId);
            } else {
                operation = "providerUnavailable";
                result = JsfExAssessService.providerUnavailable(traceId);
            }
        } catch (RuntimeException exception) {
            providerError = exception.getClass().getSimpleName() + ": " + safeMessage(exception.getMessage());
            result = OperationResult.of(OperationStatus.ERROR, safeMessage(exception.getMessage()), traceId, null);
        }
        long providerEnded = System.nanoTime();
        Instant providerEndedAt = Instant.now();

        String rawStatus = result.getStatus().name();
        String actualBehavior = compatibilityBehavior(scenario, rawStatus);
        boolean passed = matches(contractCase.getExpectedBehavior(), scenario, rawStatus);
        long responseStarted = System.nanoTime();
        Instant responseStartedAt = Instant.now();
        String responseJson = "{"
                + "\"trace_id\":" + quote(traceId)
                + ",\"status\":" + quote(rawStatus)
                + ",\"message\":" + quote(result.getMessage())
                + ",\"has_data\":" + (result.getData() != null)
                + ",\"data\":" + dataJson(result.getData())
                + "}";
        long responseEnded = System.nanoTime();
        Instant responseEndedAt = Instant.now();
        Map<String, String> consumerAttributes = mapOf(
                "capability", capability,
                "scenario", scenario,
                "request_shape", contractCase.getRequestShape(),
                "request_json", requestJson,
                "duration_ns", String.valueOf(Math.max(0L, providerEnded - consumerStarted)));
        Map<String, String> providerAttributes = mapOf(
                "operation", operation,
                "capability", capability,
                "status", rawStatus,
                "has_data", String.valueOf(result.getData() != null),
                "request_json", requestJson,
                "response_json", responseJson,
                "duration_ns", String.valueOf(Math.max(0L, providerEnded - providerStarted)));
        if (providerError != null) {
            providerAttributes.put("error", providerError);
        } else if (isErrorStatus(rawStatus)) {
            providerAttributes.put("error", result.getMessage());
        }
        Map<String, String> responseAttributes = mapOf(
                "status", rawStatus,
                "response_json", responseJson,
                "duration_ns", String.valueOf(Math.max(0L, responseEnded - responseStarted)));
        trace.append(new TraceSpan("span-" + contractCase.getCaseId() + "-consumer", traceId, "consumer",
                consumerStartedAt.toString(), providerEndedAt.toString(), durationMs(consumerStarted, providerEnded),
                providerError == null ? "OK" : "ERROR", consumerAttributes));
        trace.append(new TraceSpan("span-" + contractCase.getCaseId() + "-provider", traceId, "provider",
                providerStartedAt.toString(), providerEndedAt.toString(), durationMs(providerStarted, providerEnded),
                rawStatus, providerAttributes));
        trace.append(new TraceSpan("span-" + contractCase.getCaseId() + "-response", traceId, "response",
                responseStartedAt.toString(), responseEndedAt.toString(), durationMs(responseStarted, responseEnded),
                rawStatus, responseAttributes));
        trace.seal();
        String traceJson = traceJson(trace);
        return new ContractExecution(contractCase.getCaseId(), capability, scenario,
                contractCase.getRequestShape(), contractCase.getExpectedBehavior(), rawStatus,
                actualBehavior, traceId, passed, ExperimentRunReport.DATA_IDENTITY,
                requestJson, responseJson, traceJson);
    }

    private static long durationMs(long started, long ended) {
        return Math.max(0L, (ended - started) / 1000000L);
    }

    private static boolean isErrorStatus(String status) {
        return OperationStatus.ERROR.name().equals(status)
                || OperationStatus.INVALID_INPUT.name().equals(status)
                || OperationStatus.NOT_FOUND.name().equals(status);
    }

    private static String traceJson(Trace trace) {
        StringBuilder builder = new StringBuilder("{")
                .append("\"run_id\":").append(quote(trace.getRunId()))
                .append(",\"trace_id\":").append(quote(trace.getTraceId()))
                .append(",\"started_at\":").append(quote(trace.getStartedAt()))
                .append(",\"sealed\":").append(trace.isSealed())
                .append(",\"spans\":[");
        List<TraceSpan> spans = trace.getSpans();
        for (int index = 0; index < spans.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            TraceSpan span = spans.get(index);
            builder.append("{\"span_id\":").append(quote(span.getSpanId()))
                    .append(",\"trace_id\":").append(quote(span.getTraceId()))
                    .append(",\"name\":").append(quote(span.getName()))
                    .append(",\"started_at\":").append(quote(span.getStartedAt()))
                    .append(",\"ended_at\":").append(quote(span.getEndedAt()))
                    .append(",\"duration_ms\":").append(span.getDurationMs())
                    .append(",\"status\":").append(quote(span.getStatus()))
                    .append(",\"attributes\":").append(traceAttributesJson(span.getAttributes()))
                    .append('}');
        }
        return builder.append("]}").toString();
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
        StringBuilder builder = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\' || character == '\"') {
                builder.append('\\').append(character);
            } else if (character == '\n') {
                builder.append("\\n");
            } else if (character == '\r') {
                builder.append("\\r");
            } else if (character == '\t') {
                builder.append("\\t");
            } else if (character < 0x20) {
                builder.append(String.format("\\u%04x", (int) character));
            } else {
                builder.append(character);
            }
        }
        return builder.append('"').toString();
    }

    private static String safeMessage(String message) {
        return message == null || message.trim().isEmpty() ? "provider call failed" : message;
    }

    private static Map<String, Object> requestArguments(ContractCase contractCase) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        String shape = contractCase.getRequestShape();
        if (shape == null || shape.trim().isEmpty()) {
            return result;
        }
        for (String item : shape.split(";", -1)) {
            int separator = item.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException("invalid request shape for " + contractCase.getCaseId()
                        + ": " + shape);
            }
            String name = item.substring(0, separator).trim();
            String value = item.substring(separator + 1).trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("request shape field must not be blank");
            }
            result.put(name, "null".equalsIgnoreCase(value) ? null : value);
        }
        return result;
    }

    private static Map<String, String> mapOf(String... values) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(values[index], values[index + 1]);
        }
        return result;
    }

    private static String traceAttributesJson(Map<String, String> values) {
        StringBuilder builder = new StringBuilder("{");
        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (index++ > 0) {
                builder.append(',');
            }
            builder.append(quote(entry.getKey())).append(':').append(quote(entry.getValue()));
        }
        return builder.append('}').toString();
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

    private static String mapJson(java.util.Map<String, ?> values) {
        StringBuilder builder = new StringBuilder("{");
        int index = 0;
        for (java.util.Map.Entry<String, ?> entry : values.entrySet()) {
            if (index++ > 0) {
                builder.append(',');
            }
            builder.append(quote(entry.getKey())).append(':');
            Object value = entry.getValue();
            if (value == null) {
                builder.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                builder.append(String.valueOf(value));
            } else {
                builder.append(quote(String.valueOf(value)));
            }
        }
        return builder.append('}').toString();
    }
}
