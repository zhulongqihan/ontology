package cn.finalartical.reproduction.admin;

import cn.finalartical.reproduction.flexible.ExecutionSnapshot;
import cn.finalartical.reproduction.flexible.ExecutionStatus;
import cn.finalartical.reproduction.flexible.FieldDefinition;
import cn.finalartical.reproduction.flexible.FieldType;
import cn.finalartical.reproduction.flexible.FlexibleEngine;
import cn.finalartical.reproduction.flexible.UnknownFieldPolicy;
import cn.finalartical.reproduction.flexible.WorkflowDefinition;
import cn.finalartical.reproduction.flexible.WorkflowTransition;
import cn.finalartical.reproduction.ontology.OntologyRelationDefinition;
import cn.finalartical.reproduction.ontology.OntologyTypeDefinition;
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
    private final OntologyProvider ontologyProvider;
    private final ObjectMapper mapper = new ObjectMapper();

    EngineRuntimeService(EngineStateRepository repository, EngineState state) {
        if (repository == null || state == null) {
            throw new IllegalArgumentException("runtime repository and state must be valid");
        }
        this.repository = repository;
        this.state = state;
        this.ontologyProvider = new LocalOntologyProvider();
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
        String retryOfRunId = textValue(payload == null ? null : payload.get("retryOfRunId"), "").trim();
        int attempt = intValue(payload == null ? null : payload.get("attempt"), 1);
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
        int beforeSchemaVersion = context.getSchemaVersion();
        int beforeWorkflowVersion = context.getWorkflowVersion();
        Map<String, Object> contextValues = new LinkedHashMap<String, Object>(context.getValues());
        Map<String, Object> beforeValues = new LinkedHashMap<String, Object>(contextValues);
        Map<String, Object> executionValues = migrateContextValues(model, contextValues, beforeSchemaVersion);
        String fromState = context.getState();
        int schemaVersion = model.getSchemaVersion();
        int workflowVersion = model.getWorkflowVersion();
        List<FieldDefinition> definitions = definitions(currentSchema(model));
        FlexibleEngine engine = new FlexibleEngine(definitions, workflow(model, workflowVersion), fromState);
        for (Map.Entry<String, Object> entry : executionValues.entrySet()) {
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
        Map<String, String> validationAttributes = mapOf("errorCount", String.valueOf(errors.size()),
                "schemaFromVersion", String.valueOf(beforeSchemaVersion),
                "schemaToVersion", String.valueOf(schemaVersion),
                "schemaMigrationApplied", String.valueOf(beforeSchemaVersion != schemaVersion));
        addSpan(spans, traceId, "validation", validationStarted, errors.isEmpty() ? "OK" : "FAILED",
                validationAttributes);

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
        boolean ontologyRequested = (payload != null && payload.get("ontology") != null)
                || engine.values().get("subjects") instanceof List;
        long ontologyStarted = System.nanoTime();
        if (errors.isEmpty()) {
            try {
                if (ontologyRequested) {
                    if ("unknown".equals(ontologyTypeIdOrUnknown(modelId))) {
                        throw new IllegalArgumentException("ontology type not found: " + modelId);
                    }
                }
            } catch (IllegalArgumentException exception) {
                errors.add(exception.getMessage());
                errorCode = "ONTOLOGY_ASSEMBLY_ERROR";
            }
        }
        addSpan(spans, traceId, "ontology", ontologyStarted, errors.isEmpty() ? "OK" : "FAILED",
                mapOf("requested", String.valueOf(ontologyRequested),
                        "rootType", ontologyRequested ? ontologyTypeIdOrUnknown(modelId) : "none"));

        long providerStarted = System.nanoTime();
        Instant providerStartedAt = Instant.now();
        String providerStatus = "SKIPPED";
        String providerError = null;
        ServiceRegistration provider = service("ontology-assembler");
        Map<String, Object> providerRequest = new LinkedHashMap<String, Object>();
        providerRequest.put("serviceId", "ontology-assembler");
        providerRequest.put("operation", "assembleOntology");
        providerRequest.put("modelId", modelId);
        providerRequest.put("contextId", contextId);
        providerRequest.put("values", engine.values());
        providerRequest.put("ontology", payload == null ? null : payload.get("ontology"));
        if (!ontologyRequested) {
            providerError = "ontology input not requested";
        } else if (!errors.isEmpty()) {
            providerError = "previous stage failed";
        } else {
            try {
                ontologyGraph = invokeOntologyProvider(provider, modelId, contextId, engine.values(),
                        payload == null ? null : payload.get("ontology"));
                providerStatus = "OK";
            } catch (IllegalArgumentException exception) {
                errors.add(exception.getMessage());
                errorCode = "ONTOLOGY_ASSEMBLY_ERROR";
                providerStatus = "FAILED";
                providerError = exception.getMessage();
            }
        }
        long providerEnded = System.nanoTime();
        Instant providerEndedAt = Instant.now();
        Map<String, String> providerAttributes = mapOf(
                "serviceId", "ontology-assembler",
                "operation", "assembleOntology",
                "transport", "IN_PROCESS",
                "requestJson", json(providerRequest),
                "responseJson", providerStatus.equals("OK") ? json(ontologyGraph) : "null",
                "objectCount", String.valueOf(objectCount(ontologyGraph)));
        if (provider != null) {
            providerAttributes.put("endpoint", provider.getEndpoint());
            providerAttributes.put("provider", provider.getProvider());
            providerAttributes.put("version", provider.getVersion());
        }
        if (providerError != null) {
            providerAttributes.put("SKIPPED".equals(providerStatus) ? "skipReason" : "error", providerError);
        }
        providerAttributes.put("durationNs", String.valueOf(Math.max(0L, providerEnded - providerStarted)));
        addSpan(spans, traceId, "provider", providerStarted, providerStartedAt, providerEnded,
                providerEndedAt, providerStatus, providerAttributes);

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
        run.setRetryOfRunId(retryOfRunId.isEmpty() ? null : retryOfRunId);
        run.setAttempt(attempt);
        run.setContextRevision(passed ? context.getRevision() + 1L : context.getRevision());
        run.setContextCommitted(passed);
        run.setErrorCode(errorCode);
        run.setInputValues(inputValues);
        run.setValues(afterValues);
        run.setOntologyGraph(ontologyGraph);
        run.setValidationErrors(errors);
        run.setBeforeSnapshot(snapshot("BEFORE", contextId, modelId, beforeSchemaVersion, beforeWorkflowVersion,
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

    synchronized RuntimeRun retry(String runId) {
        RuntimeRun original = findRun(runId);
        if (original == null) {
            throw new IllegalArgumentException("run not found: " + runId);
        }
        if (!"FAILED".equals(original.getStatus())) {
            throw new IllegalArgumentException("only FAILED runs can be retried: " + runId);
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("modelId", original.getModelId());
        payload.put("contextId", original.getContextId());
        payload.put("event", original.getEvent());
        payload.put("values", new LinkedHashMap<String, Object>(original.getInputValues()));
        payload.put("retryOfRunId", original.getId());
        payload.put("attempt", original.getAttempt() + 1);
        return execute(payload);
    }

    synchronized RuntimeRun rollback(String runId) {
        RuntimeRun original = findRun(runId);
        if (original == null) {
            throw new IllegalArgumentException("run not found: " + runId);
        }
        if (!"PASSED".equals(original.getStatus())) {
            throw new IllegalArgumentException("only PASSED runs can be rolled back: " + runId);
        }
        if (original.getBeforeSnapshot() == null) {
            throw new IllegalArgumentException("run has no BEFORE snapshot: " + runId);
        }
        RuntimeContextRecord context = findContext(original.getModelId(), original.getContextId());
        if (context == null) {
            throw new IllegalArgumentException("context not found: " + original.getContextId());
        }
        if (!runId.equals(context.getLastRunId())) {
            throw new ConcurrentModificationException("cannot rollback run after a newer context revision: " + runId);
        }

        String originalStateJson;
        try {
            originalStateJson = mapper.writeValueAsString(state);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot prepare rollback transaction", exception);
        }

        long startedAt = System.nanoTime();
        String rollbackRunId = "run-" + UUID.randomUUID().toString().substring(0, 8);
        String traceId = "trace-" + rollbackRunId;
        String startedAtIso = Instant.now().toString();
        List<TraceSpanRecord> spans = new ArrayList<TraceSpanRecord>();
        addSpan(spans, traceId, "request", System.nanoTime(), "OK",
                mapOf("runId", runId, "contextId", original.getContextId()));

        Map<String, Object> beforeValues = new LinkedHashMap<String, Object>(context.getValues());
        String beforeState = context.getState();
        String beforeStatus = contextStatus(context);
        Map<String, Object> targetValues = new LinkedHashMap<String, Object>(original.getBeforeSnapshot().getValues());
        String targetState = original.getBeforeSnapshot().getState();
        addSpan(spans, traceId, "rollback", System.nanoTime(), "OK",
                mapOf("fromState", beforeState, "toState", targetState));

        RuntimeRun rollback = new RuntimeRun();
        rollback.setId(rollbackRunId);
        rollback.setModelId(original.getModelId());
        rollback.setContextId(original.getContextId());
        rollback.setEngineVersion(state.getEngineVersion());
        rollback.setSchemaVersion(original.getSchemaVersion());
        rollback.setWorkflowVersion(original.getWorkflowVersion());
        rollback.setStatus("ROLLED_BACK");
        rollback.setDataIdentity(EngineAdminService.DATA_IDENTITY);
        rollback.setEvent("rollback");
        rollback.setFromState(beforeState);
        rollback.setToState(targetState);
        rollback.setTraceId(traceId);
        rollback.setCreatedAt(startedAtIso);
        rollback.setIdempotencyKey(null);
        rollback.setRetryOfRunId(null);
        rollback.setAttempt(1);
        rollback.setContextRevision(context.getRevision() + 1L);
        rollback.setContextCommitted(true);
        rollback.setErrorCode(null);
        rollback.setInputValues(Collections.<String, Object>emptyMap());
        rollback.setValues(targetValues);
        rollback.setOntologyGraph(Collections.<String, Object>emptyMap());
        rollback.setValidationErrors(Collections.<String>emptyList());
        rollback.setBeforeSnapshot(snapshot("BEFORE", original.getContextId(), original.getModelId(),
                original.getSchemaVersion(), original.getWorkflowVersion(), beforeState, beforeStatus,
                startedAtIso, beforeValues));
        rollback.setAfterSnapshot(snapshot("AFTER", original.getContextId(), original.getModelId(),
                original.getSchemaVersion(), original.getWorkflowVersion(), targetState, "ROLLED_BACK",
                Instant.now().toString(), targetValues));

        context.setState(targetState);
        context.setStatus("ROLLED_BACK");
        context.setValues(targetValues);
        context.setRevision(context.getRevision() + 1L);
        context.setLastRunId(rollbackRunId);
        context.setLastSnapshotSha256(rollback.getAfterSnapshot().getSha256());
        context.setUpdatedAt(Instant.now().toString());

        state.getRuns().add(0, rollback);
        while (state.getRuns().size() > 50) {
            state.getRuns().remove(state.getRuns().size() - 1);
        }
        state.getAuditEvents().add(0, new AuditEventRecord(
                "audit-" + UUID.randomUUID().toString().substring(0, 8), "RUN_ROLLED_BACK", "RuntimeRun",
                runId, Instant.now().toString(), "rollbackRunId=" + rollbackRunId));
        while (state.getAuditEvents().size() > 200) {
            state.getAuditEvents().remove(state.getAuditEvents().size() - 1);
        }

        long persistenceStarted = System.nanoTime();
        addSpan(spans, traceId, "persistence", persistenceStarted, "OK",
                mapOf("contextCommitted", "true", "rollbackOfRunId", runId));
        long responseStarted = System.nanoTime();
        addSpan(spans, traceId, "response", responseStarted, "OK", Collections.<String, String>emptyMap());
        long durationMs = Math.max(1L, (System.nanoTime() - startedAt) / 1000000L);
        rollback.setDurationMs(durationMs);
        rollback.setTrace(trace(rollback, startedAtIso, spans, "ROLLED_BACK", durationMs));

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
        return rollback;
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

    private Map<String, Object> invokeOntologyProvider(ServiceRegistration provider, String modelId,
                                                       String contextId, Map<String, Object> values, Object input) {
        if (provider == null) {
            throw new IllegalArgumentException("ontology-assembler provider is not registered");
        }
        if (!"READY".equalsIgnoreCase(provider.getStatus())) {
            throw new IllegalArgumentException("ontology-assembler provider is not ready: " + provider.getStatus());
        }
        if (!"LocalOntologyProvider".equals(provider.getProvider())) {
            throw new IllegalArgumentException("ontology-assembler provider implementation is not available in-process: "
                    + provider.getProvider());
        }
        return ontologyProvider.assemble(modelId, contextId, values, input, ontologyDefinitions());
    }

    private ServiceRegistration service(String serviceId) {
        for (ServiceRegistration candidate : state.getServices()) {
            if (serviceId.equals(candidate.getId())) {
                return candidate;
            }
        }
        return null;
    }

    private String ontologyTypeIdOrUnknown(String modelId) {
        for (OntologyTypeConfig type : state.getOntologyTypes()) {
            if (modelId.equals(type.getId()) || (type.getLabel() != null && modelId.equalsIgnoreCase(type.getLabel()))) {
                return type.getId();
            }
        }
        return "unknown";
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

    private Map<String, Object> migrateContextValues(EngineModel model, Map<String, Object> source,
                                                      int fromVersion) {
        if (fromVersion > model.getSchemaVersion()) {
            throw new IllegalStateException("runtime context schema version " + fromVersion
                    + " is ahead of model schema version " + model.getSchemaVersion());
        }
        if (fromVersion == model.getSchemaVersion()) {
            return new LinkedHashMap<String, Object>(source);
        }
        Map<String, Object> migrated = new LinkedHashMap<String, Object>(source);
        for (int version = fromVersion + 1; version <= model.getSchemaVersion(); version++) {
            SchemaVersionRecord targetSchema = schemaVersion(model, version);
            Map<String, Object> next = new LinkedHashMap<String, Object>();
            for (EngineField field : targetSchema.getFields()) {
                if (migrated.containsKey(field.getName())) {
                    next.put(field.getName(), migrated.get(field.getName()));
                    continue;
                }
                boolean renamed = false;
                for (SchemaMigrationRecord rule : model.getSchemaMigrations()) {
                    if (rule.getToVersion() == version && field.getName().equals(rule.getTargetField())
                            && migrated.containsKey(rule.getSourceField())) {
                        next.put(field.getName(), migrated.get(rule.getSourceField()));
                        renamed = true;
                        break;
                    }
                }
                if (!renamed && field.getDefaultValue() != null) {
                    next.put(field.getName(), field.getDefaultValue());
                }
            }
            migrated = next;
        }
        return migrated;
    }

    private SchemaVersionRecord schemaVersion(EngineModel model, int version) {
        for (SchemaVersionRecord candidate : model.getSchemaVersions()) {
            if (candidate.getVersion() == version) {
                return candidate;
            }
        }
        if (version == model.getSchemaVersion()) {
            return currentSchema(model);
        }
        throw new IllegalStateException("schema version not found for runtime migration: " + version);
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
        long endedAt = System.nanoTime();
        long elapsedNanos = Math.max(0L, endedAt - startedAt);
        Instant endedAtIso = Instant.now();
        Instant startedAtIso = endedAtIso.minusNanos(elapsedNanos);
        addSpan(spans, traceId, name, startedAt, startedAtIso, endedAt, endedAtIso, status, attributes);
    }

    private void addSpan(List<TraceSpanRecord> spans, String traceId, String name, long startedAt,
                         Instant startedAtIso, long endedAt, Instant endedAtIso, String status,
                         Map<String, String> attributes) {
        spans.add(new TraceSpanRecord("span-" + UUID.randomUUID().toString().substring(0, 8), traceId, name,
                startedAtIso.toString(), endedAtIso.toString(), Math.max(0L, (endedAt - startedAt) / 1000000L),
                status, attributes));
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot serialize provider evidence", exception);
        }
    }

    private List<OntologyTypeDefinition> ontologyDefinitions() {
        List<OntologyTypeDefinition> definitions = new ArrayList<OntologyTypeDefinition>();
        for (OntologyTypeConfig type : state.getOntologyTypes()) {
            List<OntologyRelationDefinition> relations = new ArrayList<OntologyRelationDefinition>();
            for (OntologyRelationConfig relation : type.getRelations()) {
                relations.add(new OntologyRelationDefinition(relation.getName(), relation.getTargetType(),
                        relation.getCardinality()));
            }
            definitions.add(new OntologyTypeDefinition(type.getId(), type.getLabel(), type.getFixedAttributes(),
                    type.getDynamicAttributes(), relations));
        }
        return definitions;
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

    private static int intValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            int result = Integer.parseInt(String.valueOf(value));
            if (result < 1) {
                throw new IllegalArgumentException("attempt must be positive");
            }
            return result;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("attempt must be an integer");
        }
    }

    private static Map<String, String> mapOf(String... values) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(values[index], values[index + 1]);
        }
        return result;
    }
}
