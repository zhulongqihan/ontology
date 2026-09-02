package cn.finalartical.reproduction.admin;

import cn.finalartical.reproduction.flexible.ExecutionSnapshot;
import cn.finalartical.reproduction.flexible.ExecutionStatus;
import cn.finalartical.reproduction.flexible.FieldDefinition;
import cn.finalartical.reproduction.flexible.FieldType;
import cn.finalartical.reproduction.flexible.FlexibleEngine;
import cn.finalartical.reproduction.flexible.UnknownFieldPolicy;
import cn.finalartical.reproduction.flexible.WorkflowDefinition;
import cn.finalartical.reproduction.flexible.WorkflowTransition;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class EngineRuntimeService {
    private final EngineStateRepository repository;
    private final EngineState state;
    private final ObjectMapper mapper = new ObjectMapper();

    EngineRuntimeService(EngineStateRepository repository, EngineState state) {
        if (repository == null || state == null) {
            throw new IllegalArgumentException("runtime repository and state must be valid");
        }
        this.repository = repository;
        this.state = state;
    }

    synchronized RuntimeRun execute(Map<String, Object> payload) {
        long startedAt = System.nanoTime();
        String modelId = requiredText(payload, "modelId");
        EngineModel model = model(modelId);
        Map<String, Object> inputValues = mapValue(payload == null ? null : payload.get("values"));
        String event = textValue(payload == null ? null : payload.get("event"), "").trim();
        String contextId = textValue(payload == null ? null : payload.get("contextId"), "").trim();
        if (contextId.isEmpty()) {
            contextId = "ctx-" + UUID.randomUUID().toString().substring(0, 8);
        }
        String idempotencyKey = textValue(payload == null ? null : payload.get("idempotencyKey"), "").trim();
        String scope = modelId + "|" + contextId;
        String requestSha256 = requestSha256(modelId, contextId, event, inputValues);
        if (!idempotencyKey.isEmpty()) {
            if (idempotencyKey.length() > 200) {
                throw new IllegalArgumentException("idempotencyKey must be 1-200 characters");
            }
            IdempotencyRecord existing = idempotency(scope, idempotencyKey);
            if (existing != null) {
                if (!requestSha256.equals(existing.getRequestSha256())) {
                    throw new IllegalArgumentException("idempotency key already used with a different request");
                }
                RuntimeRun prior = findRun(existing.getRunId());
                if (prior == null) {
                    throw new IllegalStateException("idempotency record points to a missing run: " + existing.getRunId());
                }
                return prior;
            }
        }

        String originalStateJson;
        try {
            originalStateJson = mapper.writeValueAsString(state);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot prepare runtime transaction", exception);
        }

        String runId = "run-" + UUID.randomUUID().toString().substring(0, 8);
        String traceId = "trace-" + runId;
        String startedAtIso = Instant.now().toString();
        List<TraceSpanRecord> spans = new ArrayList<TraceSpanRecord>();
        addSpan(spans, traceId, "request", System.nanoTime(), "OK", mapOf("modelId", modelId, "contextId", contextId));

        RuntimeContextRecord context = findContext(modelId, contextId);
        boolean newContext = context == null;
        if (newContext) {
            context = new RuntimeContextRecord(contextId, modelId, model.getSchemaVersion(), model.getWorkflowVersion(),
                    model.getInitialState(), "CREATED", 0L, startedAtIso);
            context.setValues(defaultValues(currentSchema(model)));
        }
        Map<String, Object> beforeValues = new LinkedHashMap<String, Object>(context.getValues());
        String fromState = context.getState();
        int schemaVersion = model.getSchemaVersion();
        int workflowVersion = model.getWorkflowVersion();
        List<FieldDefinition> definitions = definitions(currentSchema(model));
        FlexibleEngine engine = new FlexibleEngine(definitions, workflow(model, workflowVersion), fromState);
        for (Map.Entry<String, Object> entry : beforeValues.entrySet()) {
            engine.set(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Object> entry : inputValues.entrySet()) {
            engine.set(entry.getKey(), entry.getValue());
        }

        List<String> errors = new ArrayList<String>();
        String errorCode = null;
        String nextState = fromState;
        long validationStarted = System.nanoTime();
        try {
            errors.addAll(engine.validate(policy(model)));
        } catch (IllegalArgumentException exception) {
            errors.add(exception.getMessage());
        }
        if (!errors.isEmpty()) {
            errorCode = "VALIDATION_ERROR";
        }
        addSpan(spans, traceId, "validation", validationStarted, errors.isEmpty() ? "OK" : "FAILED",
                mapOf("errorCount", String.valueOf(errors.size())));

        long workflowStarted = System.nanoTime();
        if (errors.isEmpty()) {
            if (event.isEmpty()) {
                errors.add("event is required");
                errorCode = "EVENT_REQUIRED";
            } else {
                try {
                    nextState = engine.apply(event);
                } catch (IllegalStateException exception) {
                    errors.add(exception.getMessage());
                    errorCode = "INVALID_EVENT";
                }
            }
        }
        addSpan(spans, traceId, "workflow", workflowStarted, errors.isEmpty() ? "OK" : "FAILED",
                mapOf("event", event, "fromState", fromState, "toState", errors.isEmpty() ? nextState : fromState));

        Map<String, Object> ontologyGraph = new LinkedHashMap<String, Object>();
        long ontologyStarted = System.nanoTime();
        if (errors.isEmpty()) {
            try {
                ontologyGraph = assembleOntology(modelId, contextId, engine.values(), payload == null ? null : payload.get("ontology"));
            } catch (IllegalArgumentException exception) {
                errors.add(exception.getMessage());
                errorCode = "ONTOLOGY_ASSEMBLY_ERROR";
            }
        }
        addSpan(spans, traceId, "ontology", ontologyStarted, errors.isEmpty() ? "OK" : "FAILED",
                mapOf("objectCount", String.valueOf(objectCount(ontologyGraph))));

        boolean passed = errors.isEmpty();
        String status = passed ? "PASSED" : "FAILED";
        String toState = passed ? nextState : fromState;
        Map<String, Object> afterValues = new LinkedHashMap<String, Object>(engine.values());
        RuntimeRun run = new RuntimeRun();
        run.setId(runId);
        run.setModelId(modelId);
        run.setContextId(contextId);
        run.setEngineVersion(state.getEngineVersion());
        run.setSchemaVersion(schemaVersion);
        run.setWorkflowVersion(workflowVersion);
        run.setStatus(status);
        run.setDataIdentity(EngineAdminService.DATA_IDENTITY);
        run.setEvent(event);
        run.setFromState(fromState);
        run.setToState(toState);
        run.setTraceId(traceId);
        run.setCreatedAt(startedAtIso);
        run.setIdempotencyKey(idempotencyKey.isEmpty() ? null : idempotencyKey);
        run.setContextRevision(passed ? context.getRevision() + 1L : context.getRevision());
        run.setContextCommitted(passed);
        run.setErrorCode(errorCode);
        run.setValues(afterValues);
        run.setOntologyGraph(ontologyGraph);
        run.setValidationErrors(errors);
        run.setBeforeSnapshot(snapshot("BEFORE", contextId, modelId, schemaVersion, workflowVersion,
                fromState, contextStatus(context), startedAtIso, beforeValues));
        run.setAfterSnapshot(snapshot("AFTER", contextId, modelId, schemaVersion, workflowVersion,
                toState, status, Instant.now().toString(), afterValues));

        if (passed) {
            context.setSchemaVersion(schemaVersion);
            context.setWorkflowVersion(workflowVersion);
            context.setState(toState);
            context.setStatus(status);
            context.setValues(afterValues);
            context.setRevision(context.getRevision() + 1L);
            context.setLastRunId(runId);
            context.setLastSnapshotSha256(run.getAfterSnapshot().getSha256());
            context.setUpdatedAt(Instant.now().toString());
        }
        if (newContext) {
            state.getContexts().add(context);
        }

        state.getRuns().add(0, run);
        while (state.getRuns().size() > 50) {
            state.getRuns().remove(state.getRuns().size() - 1);
        }
        if (!idempotencyKey.isEmpty()) {
            state.getIdempotencyRecords().add(0, new IdempotencyRecord(scope, idempotencyKey, requestSha256,
                    runId, Instant.now().toString()));
        }

        long persistenceStarted = System.nanoTime();
        addSpan(spans, traceId, "persistence", persistenceStarted, "OK",
                mapOf("contextCommitted", String.valueOf(passed)));
        long responseStarted = System.nanoTime();
        addSpan(spans, traceId, "response", responseStarted, "OK", Collections.<String, String>emptyMap());
        long durationMs = Math.max(1L, (System.nanoTime() - startedAt) / 1000000L);
        run.setDurationMs(durationMs);
        TraceRecord trace = trace(run, startedAtIso, spans, status, durationMs);
        run.setTrace(trace);

        try {
            state.setUpdatedAt(Instant.now().toString());
            repository.save(state, state.getRevision());
        } catch (RuntimeException exception) {
            restoreState(originalStateJson);
            if (exception instanceof ConcurrentModificationException) {
                throw (ConcurrentModificationException) exception;
            }
            throw exception;
        }
        return run;
    }

    private void restoreState(String stateJson) {
        try {
            EngineState restored = mapper.readValue(stateJson, EngineState.class);
            state.setEngineId(restored.getEngineId());
            state.setEngineName(restored.getEngineName());
            state.setEngineVersion(restored.getEngineVersion());
            state.setUpdatedAt(restored.getUpdatedAt());
            state.setRevision(restored.getRevision());
            state.setModels(restored.getModels());
            state.setOntologyTypes(restored.getOntologyTypes());
            state.setServices(restored.getServices());
            state.setRuns(restored.getRuns());
            state.setContexts(restored.getContexts());
            state.setAuditEvents(restored.getAuditEvents());
            state.setIdempotencyRecords(restored.getIdempotencyRecords());
        } catch (IOException exception) {
            throw new IllegalStateException("cannot restore failed runtime transaction", exception);
        }
    }

    private EngineModel model(String modelId) {
        for (EngineModel model : state.getModels()) {
            if (modelId.equals(model.getId())) {
                return model;
            }
        }
        throw new IllegalArgumentException("model not found: " + modelId);
    }

    private SchemaVersionRecord currentSchema(EngineModel model) {
        for (SchemaVersionRecord version : model.getSchemaVersions()) {
            if (version.getVersion() == model.getSchemaVersion()) {
                return version;
            }
        }
        return new SchemaVersionRecord(model.getSchemaVersion(), model.getUpdatedAt(), model.getFields());
    }

    private WorkflowDefinition workflow(EngineModel model, int version) {
        WorkflowVersionRecord selected = null;
        for (WorkflowVersionRecord candidate : model.getWorkflowVersions()) {
            if (candidate.getVersion() == version) {
                selected = candidate;
                break;
            }
        }
        List<EngineTransition> transitions = selected == null ? model.getTransitions() : selected.getTransitions();
        String initialState = selected == null ? model.getInitialState() : selected.getInitialState();
        List<WorkflowTransition> definitions = new ArrayList<WorkflowTransition>();
        for (EngineTransition transition : transitions) {
            definitions.add(new WorkflowTransition(transition.getFromState(), transition.getEvent(), transition.getToState()));
        }
        return new WorkflowDefinition(initialState, definitions);
    }

    private List<FieldDefinition> definitions(SchemaVersionRecord schema) {
        List<FieldDefinition> definitions = new ArrayList<FieldDefinition>();
        for (EngineField field : schema.getFields()) {
            definitions.add(new FieldDefinition(field.getName(), FieldType.valueOf(field.getType()),
                    field.isRequired(), field.getVersion(), field.getDefaultValue()));
        }
        return definitions;
    }

    private Map<String, Object> defaultValues(SchemaVersionRecord schema) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        for (EngineField field : schema.getFields()) {
            if (field.getDefaultValue() != null) {
                values.put(field.getName(), field.getDefaultValue());
            }
        }
        return values;
    }

    private UnknownFieldPolicy policy(EngineModel model) {
        try {
            return UnknownFieldPolicy.valueOf(model.getUnknownFieldPolicy().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return UnknownFieldPolicy.REJECT;
        }
    }

    private ExecutionSnapshotRecord snapshot(String phase, String contextId, String modelId, int schemaVersion,
                                             int workflowVersion, String state, String status, String capturedAt,
                                             Map<String, Object> values) {
        ExecutionStatus executionStatus;
        try {
            executionStatus = ExecutionStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            executionStatus = ExecutionStatus.CREATED;
        }
        ExecutionSnapshot snapshot = new ExecutionSnapshot(contextId, modelId, schemaVersion, workflowVersion,
                state, executionStatus, capturedAt, values);
        ExecutionSnapshotRecord record = new ExecutionSnapshotRecord();
        record.setPhase(phase);
        record.setContextId(contextId);
        record.setModelId(modelId);
        record.setSchemaVersion(schemaVersion);
        record.setWorkflowVersion(workflowVersion);
        record.setState(state);
        record.setStatus(status);
        record.setCapturedAt(capturedAt);
        record.setValues(values);
        record.setSha256(snapshot.getSha256());
        return record;
    }

    private TraceRecord trace(RuntimeRun run, String startedAt, List<TraceSpanRecord> spans, String status, long durationMs) {
        TraceRecord trace = new TraceRecord();
        trace.setRunId(run.getId());
        trace.setTraceId(run.getTraceId());
        trace.setStartedAt(startedAt);
        trace.setEndedAt(Instant.now().toString());
        trace.setDurationMs(durationMs);
        trace.setStatus(status);
        trace.setSealed(true);
        trace.setSpans(spans);
        return trace;
    }

    private void addSpan(List<TraceSpanRecord> spans, String traceId, String name, long startedAt,
                         String status, Map<String, String> attributes) {
        String now = Instant.now().toString();
        spans.add(new TraceSpanRecord("span-" + UUID.randomUUID().toString().substring(0, 8), traceId, name,
                now, now, Math.max(0L, (System.nanoTime() - startedAt) / 1000000L), status, attributes));
    }

    private Map<String, Object> assembleOntology(String modelId, String contextId, Map<String, Object> values, Object input) {
        if (input == null && !(values.get("subjects") instanceof List)) {
            return new LinkedHashMap<String, Object>();
        }
        Map<String, Object> graph = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> objects = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> relations = new ArrayList<Map<String, Object>>();
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("id", contextId);
        root.put("type", ontologyTypeId(modelId));
        root.put("attributes", new LinkedHashMap<String, Object>(values));
        objects.add(root);
        Object subjects = values.get("subjects");
        if (subjects instanceof List) {
            for (Object subjectItem : (List<?>) subjects) {
                if (!(subjectItem instanceof Map)) {
                    throw new IllegalArgumentException("ontology subjects must be objects");
                }
                Map<?, ?> source = (Map<?, ?>) subjectItem;
                String subjectId = textValue(source.get("id"), "").trim();
                String title = textValue(source.get("title"), "").trim();
                if (subjectId.isEmpty() || title.isEmpty()) {
                    throw new IllegalArgumentException("ontology subject id and title are required");
                }
                Map<String, Object> subject = new LinkedHashMap<String, Object>();
                subject.put("id", subjectId);
                subject.put("type", ontologyTypeId("subject"));
                Map<String, Object> attributes = new LinkedHashMap<String, Object>();
                attributes.put("title", title);
                subject.put("attributes", attributes);
                objects.add(subject);
                relations.add(edge(contextId, "containsSubject", subjectId));
                Object options = source.get("options");
                if (options instanceof List) {
                    for (Object optionItem : (List<?>) options) {
                        if (!(optionItem instanceof Map)) {
                            throw new IllegalArgumentException("ontology options must be objects");
                        }
                        Map<?, ?> optionSource = (Map<?, ?>) optionItem;
                        String optionId = textValue(optionSource.get("id"), "").trim();
                        String label = textValue(optionSource.get("label"), "").trim();
                        if (optionId.isEmpty() || label.isEmpty()) {
                            throw new IllegalArgumentException("ontology option id and label are required");
                        }
                        Map<String, Object> option = new LinkedHashMap<String, Object>();
                        option.put("id", optionId);
                        option.put("type", ontologyTypeId("option"));
                        option.put("attributes", Collections.singletonMap("label", label));
                        objects.add(option);
                        relations.add(edge(subjectId, "subjectContainsOption", optionId));
                    }
                }
            }
        }
        graph.put("rootObjectId", contextId);
        graph.put("objects", objects);
        graph.put("relations", relations);
        return graph;
    }

    private String ontologyTypeId(String requested) {
        for (OntologyTypeConfig type : state.getOntologyTypes()) {
            if (requested.equals(type.getId()) || requested.equalsIgnoreCase(type.getLabel())) {
                return type.getId();
            }
        }
        throw new IllegalArgumentException("ontology type not found: " + requested);
    }

    private Map<String, Object> edge(String sourceId, String relation, String targetId) {
        Map<String, Object> edge = new LinkedHashMap<String, Object>();
        edge.put("sourceId", sourceId);
        edge.put("relation", relation);
        edge.put("targetId", targetId);
        return edge;
    }

    private int objectCount(Map<String, Object> graph) {
        Object objects = graph.get("objects");
        return objects instanceof List ? ((List<?>) objects).size() : 0;
    }

    private RuntimeContextRecord findContext(String modelId, String contextId) {
        for (RuntimeContextRecord context : state.getContexts()) {
            if (modelId.equals(context.getModelId()) && contextId.equals(context.getContextId())) {
                return context;
            }
        }
        return null;
    }

    private RuntimeRun findRun(String runId) {
        for (RuntimeRun run : state.getRuns()) {
            if (runId.equals(run.getId())) {
                return run;
            }
        }
        return null;
    }

    private IdempotencyRecord idempotency(String scope, String key) {
        for (IdempotencyRecord record : state.getIdempotencyRecords()) {
            if (scope.equals(record.getScope()) && key.equals(record.getKey())) {
                return record;
            }
        }
        return null;
    }

    private String contextStatus(RuntimeContextRecord context) {
        return context.getStatus() == null ? "CREATED" : context.getStatus();
    }

    private String requestSha256(String modelId, String contextId, String event, Map<String, Object> values) {
        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put("modelId", modelId);
        request.put("contextId", contextId);
        request.put("event", event);
        request.put("values", values);
        return new cn.finalartical.reproduction.flexible.ContextSnapshot(request).getSha256();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (value == null) {
            return Collections.emptyMap();
        }
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("values must be an object");
        }
        return (Map<String, Object>) value;
    }

    private static String requiredText(Map<String, Object> payload, String key) {
        String value = textValue(payload == null ? null : payload.get(key), "").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private static String textValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static Map<String, String> mapOf(String... values) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(values[index], values[index + 1]);
        }
        return result;
    }
}
