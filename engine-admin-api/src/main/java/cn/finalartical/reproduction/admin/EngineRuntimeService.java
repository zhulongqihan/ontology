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
        return executeInternal(payload, false);
    }

    private RuntimeRun executeInternal(Map<String, Object> payload, boolean deferPersistence) {
        long startedAt = System.nanoTime();
        String executionMode = textValue(payload == null ? null : payload.get("executionMode"), "FLEXIBLE_ENGINE").trim();
        if (RigidMappingBaseline.MODE.equals(executionMode)) {
            return executeRigidBaseline(payload, deferPersistence);
        }
        if (!"FLEXIBLE_ENGINE".equals(executionMode)) {
            throw new IllegalArgumentException("unsupported executionMode: " + executionMode);
        }
        String modelId = requiredText(payload, "modelId");
        EngineModel model = model(modelId);
        Map<String, Object> inputValues = mapValue(payload == null ? null : payload.get("values"));
        String event = textValue(payload == null ? null : payload.get("event"), "").trim();
        String contextId = textValue(payload == null ? null : payload.get("contextId"), "").trim();
        if (contextId.isEmpty()) {
            contextId = "ctx-" + UUID.randomUUID().toString().substring(0, 8);
        }
        String comparisonId = textValue(payload == null ? null : payload.get("comparisonId"), "").trim();
        String caseId = textValue(payload == null ? null : payload.get("caseId"), "").trim();
        String idempotencyKey = textValue(payload == null ? null : payload.get("idempotencyKey"), "").trim();
        String retryOfRunId = textValue(payload == null ? null : payload.get("retryOfRunId"), "").trim();
        String replayOfRunId = textValue(payload == null ? null : payload.get("replayOfRunId"), "").trim();
        int attempt = intValue(payload == null ? null : payload.get("attempt"), 1);
        String replayState = textValue(payload == null ? null : payload.get("replayFromState"), "").trim();
        Map<String, Object> replayValues = mapValue(payload == null ? null : payload.get("replayValues"));
        String scope = modelId + "|" + contextId;
        Object ontologyInput = payload == null ? null : payload.get("ontology");
        String requestSha256 = requestSha256(modelId, contextId, event, inputValues, ontologyInput);
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
            if (!replayState.isEmpty()) {
                context.setState(replayState);
                context.setStatus(textValue(payload == null ? null : payload.get("replayBeforeStatus"), "CREATED"));
                context.setValues(replayValues);
            }
        }
        int beforeSchemaVersion = context.getSchemaVersion();
        int beforeWorkflowVersion = context.getWorkflowVersion();
        Map<String, Object> contextValues = mapValue(context.getValues());
        Map<String, Object> beforeValues = mapValue(contextValues);
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
        String boundOntologyTypeId = model.getOntologyTypeId();
        OntologyTypeConfig boundOntologyType = boundOntologyTypeId == null ? null : ontologyType(boundOntologyTypeId);
        int boundOntologyVersion = boundOntologyType == null ? 0 : boundOntologyType.getVersion();
        String boundOntologyDefinitionSha256 = boundOntologyType == null ? null
                : OntologyDefinitionHasher.sha256(boundOntologyType);
        String expectedOntologyVersion = textValue(payload == null ? null : payload.get("expectedOntologyVersion"), "").trim();
        String expectedOntologyHash = textValue(payload == null ? null : payload.get("expectedOntologyDefinitionSha256"), "").trim();
        if (!expectedOntologyVersion.isEmpty()
                && (boundOntologyType == null || boundOntologyVersion != positiveIntValue(expectedOntologyVersion, "expectedOntologyVersion"))) {
            throw new IllegalArgumentException("ontology definition version changed since the original run");
        }
        if (!expectedOntologyHash.isEmpty()
                && (boundOntologyType == null || !expectedOntologyHash.equals(boundOntologyDefinitionSha256))) {
            throw new IllegalArgumentException("ontology definition hash changed since the original run");
        }
        long ontologyStarted = System.nanoTime();
        if (errors.isEmpty()) {
            try {
                if (ontologyRequested) {
                    if (boundOntologyTypeId == null || boundOntologyTypeId.trim().isEmpty()) {
                        throw new IllegalArgumentException("model has no explicit ontology binding: " + modelId);
                    }
                }
            } catch (IllegalArgumentException exception) {
                errors.add(exception.getMessage());
                errorCode = "ONTOLOGY_ASSEMBLY_ERROR";
            }
        }
        addSpan(spans, traceId, "ontology", ontologyStarted, errors.isEmpty() ? "OK" : "FAILED",
                mapOf("requested", String.valueOf(ontologyRequested),
                        "rootType", ontologyRequested ? String.valueOf(boundOntologyTypeId) : "none",
                        "bindingSource", ontologyRequested ? "model.ontologyTypeId" : "none"));

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
        providerRequest.put("ontologyTypeVersion", boundOntologyVersion);
        providerRequest.put("ontologyDefinitionSha256", boundOntologyDefinitionSha256);
        if (!ontologyRequested) {
            providerError = "ontology input not requested";
        } else if (!errors.isEmpty()) {
            providerError = "previous stage failed";
        } else {
            try {
                ontologyGraph = invokeOntologyProvider(provider, modelId, boundOntologyTypeId, contextId,
                        engine.values(), ontologyInput);
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
                "ontologyTypeVersion", String.valueOf(boundOntologyVersion),
                "ontologyDefinitionSha256", String.valueOf(boundOntologyDefinitionSha256),
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
        run.setExecutionMode("FLEXIBLE_ENGINE");
        run.setComparisonId(comparisonId.isEmpty() ? null : comparisonId);
        run.setCaseId(caseId.isEmpty() ? null : caseId);
        run.setInputSha256(comparisonInputSha256(modelId, event, inputValues, ontologyInput));
        run.setConfigurationSha256(flexibleConfigurationSha256(model, boundOntologyVersion,
                boundOntologyDefinitionSha256));
        run.setOntologyTypeId(boundOntologyTypeId);
        run.setOntologyVersion(boundOntologyVersion);
        run.setOntologyDefinitionSha256(boundOntologyDefinitionSha256);
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
        run.setReplayOfRunId(replayOfRunId.isEmpty() ? null : replayOfRunId);
        run.setAttempt(attempt);
        run.setContextRevision(passed ? context.getRevision() + 1L : context.getRevision());
        run.setContextCommitted(passed);
        run.setErrorCode(errorCode);
        run.setInputValues(mapValue(inputValues));
        run.setValues(mapValue(afterValues));
        run.setOntologyGraph(ontologyGraph);
        run.setOntologyInput(copyValue(ontologyInput));
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
        addSpan(spans, traceId, "persistence", persistenceStarted, "PREPARED",
                mapOf("contextCommitted", String.valueOf(passed),
                        "commitBoundary", "repository.commit"));
        long responseStarted = System.nanoTime();
        addSpan(spans, traceId, "response", responseStarted, "PREPARED",
                mapOf("deliveryBoundary", "caller-observation"));
        long durationNs = Math.max(0L, System.nanoTime() - startedAt);
        long durationMs = Math.max(1L, durationNs / 1000000L);
        run.setDurationNs(durationNs);
        run.setDurationMs(durationMs);
        TraceRecord trace = trace(run, startedAtIso, spans, status, durationNs, durationMs);
        run.setTrace(trace);

        if (deferPersistence) {
            return run;
        }
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
        try {
            repository.markPersistenceCommitted(state, runId);
        } catch (RuntimeException markerFailure) {
            // The Run is already durable. If the post-commit observation fails,
            // retain PREPARED rather than claiming a commit that was not observed.
            try {
                restoreState(repository.load());
            } catch (RuntimeException ignored) {
                // Preserve the durable result and return the marker state below.
            }
            RuntimeRun persisted = findRun(runId);
            if (persisted != null) {
                return persisted;
            }
            throw new IllegalStateException("run committed but trace commit observation failed", markerFailure);
        }
        return run;
    }

    synchronized Map<String, Object> executeComparison(Map<String, Object> payload) {
        String modelId = requiredText(payload, "modelId");
        String comparisonId = textValue(payload.get("comparisonId"), "").trim();
        if (comparisonId.isEmpty()) {
            comparisonId = "cmp-" + UUID.randomUUID().toString().substring(0, 8);
        }
        if (comparisonId.length() > 120) {
            throw new IllegalArgumentException("comparisonId must be 1-120 characters");
        }
        String caseId = textValue(payload.get("caseId"), "manual-" + comparisonId).trim();
        if (caseId.isEmpty() || caseId.length() > 120) {
            throw new IllegalArgumentException("caseId must be 1-120 characters");
        }
        Map<String, Object> requestedInputValues = mapValue(payload == null ? null : payload.get("values"));
        String requestedEvent = textValue(payload == null ? null : payload.get("event"), "").trim();
        String requestedInputSha256 = comparisonInputSha256(modelId, requestedEvent, requestedInputValues,
                payload == null ? null : payload.get("ontology"));
        RuntimeRun existingBaseline = findComparisonRun(comparisonId, RigidMappingBaseline.MODE);
        RuntimeRun existingFlexible = findComparisonRun(comparisonId, "FLEXIBLE_ENGINE");
        if (existingBaseline != null || existingFlexible != null) {
            if (existingBaseline == null || existingFlexible == null) {
                throw new IllegalStateException("comparison exists without a complete baseline/flexible pair: " + comparisonId);
            }
            if (!sameComparisonRequest(modelId, requestedEvent, caseId, requestedInputSha256,
                    existingBaseline, existingFlexible)) {
                throw new IllegalArgumentException("comparisonId already used with a different request: " + comparisonId);
            }
            return comparisonResult(comparisonId, caseId, existingBaseline, existingFlexible);
        }

        String originalStateJson;
        try {
            originalStateJson = mapper.writeValueAsString(state);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot prepare comparison transaction", exception);
        }

        Map<String, Object> baselinePayload = copyPayload(payload);
        baselinePayload.put("modelId", modelId);
        baselinePayload.put("executionMode", RigidMappingBaseline.MODE);
        baselinePayload.put("comparisonId", comparisonId);
        baselinePayload.put("caseId", caseId);
        baselinePayload.put("contextId", "ctx-" + comparisonId + "-baseline");
        baselinePayload.put("idempotencyKey", "comparison:" + comparisonId + ":baseline");
        Map<String, Object> flexiblePayload = copyPayload(payload);
        flexiblePayload.put("modelId", modelId);
        flexiblePayload.put("executionMode", "FLEXIBLE_ENGINE");
        flexiblePayload.put("comparisonId", comparisonId);
        flexiblePayload.put("caseId", caseId);
        flexiblePayload.put("contextId", "ctx-" + comparisonId + "-flexible");
        flexiblePayload.put("idempotencyKey", "comparison:" + comparisonId + ":flexible");

        try {
            RuntimeRun baseline = executeInternal(baselinePayload, true);
            RuntimeRun flexible = executeInternal(flexiblePayload, true);
            baseline.setPairedRunId(flexible.getId());
            flexible.setPairedRunId(baseline.getId());
            state.setUpdatedAt(Instant.now().toString());
            repository.save(state, state.getRevision());
            repository.markPersistenceCommitted(state, baseline.getId());
            repository.markPersistenceCommitted(state, flexible.getId());
            return comparisonResult(comparisonId, caseId, baseline, flexible);
        } catch (RuntimeException exception) {
            try {
                restoreState(originalStateJson);
            } catch (RuntimeException ignored) {
                // Preserve the original failure; a failed comparison must not remain in memory.
            }
            throw exception;
        }
    }

    private RuntimeRun executeRigidBaseline(Map<String, Object> payload, boolean deferPersistence) {
        long startedAt = System.nanoTime();
        String modelId = requiredText(payload, "modelId");
        EngineModel model = model(modelId);
        Map<String, Object> inputValues = mapValue(payload == null ? null : payload.get("values"));
        String event = textValue(payload == null ? null : payload.get("event"), "").trim();
        String contextId = textValue(payload == null ? null : payload.get("contextId"), "").trim();
        if (contextId.isEmpty()) {
            contextId = "ctx-" + UUID.randomUUID().toString().substring(0, 8);
        }
        String comparisonId = textValue(payload == null ? null : payload.get("comparisonId"), "").trim();
        String caseId = textValue(payload == null ? null : payload.get("caseId"), "").trim();
        String idempotencyKey = textValue(payload == null ? null : payload.get("idempotencyKey"), "").trim();
        String requestSha256 = requestSha256(modelId, contextId, event, inputValues,
                payload == null ? null : payload.get("ontology"));
        String inputSha256 = comparisonInputSha256(modelId, event, inputValues,
                payload == null ? null : payload.get("ontology"));
        String scope = modelId + "|" + contextId + "|" + RigidMappingBaseline.MODE;
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
            throw new IllegalStateException("cannot prepare baseline transaction", exception);
        }
        String runId = "run-" + UUID.randomUUID().toString().substring(0, 8);
        String traceId = "trace-" + runId;
        String startedAtIso = Instant.now().toString();
        List<TraceSpanRecord> spans = new ArrayList<TraceSpanRecord>();
        addSpan(spans, traceId, "request", System.nanoTime(), "OK",
                mapOf("executionMode", RigidMappingBaseline.MODE, "modelId", modelId, "contextId", contextId));

        RuntimeContextRecord context = findContext(modelId, contextId);
        boolean newContext = context == null;
        if (newContext) {
            context = new RuntimeContextRecord(contextId, modelId, model.getSchemaVersion(), model.getWorkflowVersion(),
                    model.getInitialState(), "CREATED", 0L, startedAtIso);
            context.setValues(defaultValues(currentSchema(model)));
        }
        int beforeSchemaVersion = context.getSchemaVersion();
        int beforeWorkflowVersion = context.getWorkflowVersion();
        String fromState = context.getState();
        Map<String, Object> beforeValues = mapValue(context.getValues());
        Map<String, Object> executionValues = mapValue(beforeValues);
        executionValues.putAll(inputValues);
        long validationStarted = System.nanoTime();
        RigidMappingBaseline.Result result = new RigidMappingBaseline().execute(modelId, fromState, event,
                executionValues, payload == null ? null : payload.get("ontology"));
        addSpan(spans, traceId, "validation", validationStarted, result.passed ? "OK" : "FAILED",
                mapOf("errorCount", String.valueOf(result.errors.size()), "executionMode", RigidMappingBaseline.MODE));
        long mappingStarted = System.nanoTime();
        addSpan(spans, traceId, "mapping", mappingStarted, result.passed ? "OK" : "FAILED",
                mapOf("mapping", "fixed-field-paths", "objectCount", String.valueOf(objectCount(result.graph))));

        String status = result.passed ? "PASSED" : "FAILED";
        String toState = result.passed ? result.toState : fromState;
        Map<String, Object> afterValues = new LinkedHashMap<String, Object>(executionValues);
        RuntimeRun run = new RuntimeRun();
        run.setId(runId);
        run.setModelId(modelId);
        run.setExecutionMode(RigidMappingBaseline.MODE);
        run.setComparisonId(comparisonId.isEmpty() ? null : comparisonId);
        run.setCaseId(caseId.isEmpty() ? null : caseId);
        run.setInputSha256(inputSha256);
        run.setConfigurationSha256(fixedBaselineConfigurationSha256(modelId));
        run.setContextId(contextId);
        run.setEngineVersion(state.getEngineVersion());
        run.setSchemaVersion(model.getSchemaVersion());
        run.setWorkflowVersion(model.getWorkflowVersion());
        run.setStatus(status);
        run.setDataIdentity(EngineAdminService.DATA_IDENTITY);
        run.setEvent(event);
        run.setFromState(fromState);
        run.setToState(toState);
        run.setTraceId(traceId);
        run.setCreatedAt(startedAtIso);
        run.setIdempotencyKey(idempotencyKey.isEmpty() ? null : idempotencyKey);
        run.setAttempt(1);
        run.setContextRevision(result.passed ? context.getRevision() + 1L : context.getRevision());
        run.setContextCommitted(result.passed);
        run.setErrorCode(result.errorCode);
        run.setInputValues(inputValues);
        run.setValues(afterValues);
        run.setOntologyGraph(result.graph);
        run.setOntologyInput(copyValue(payload == null ? null : payload.get("ontology")));
        run.setValidationErrors(result.errors);
        run.setBeforeSnapshot(snapshot("BEFORE", contextId, modelId, beforeSchemaVersion, beforeWorkflowVersion,
                fromState, contextStatus(context), startedAtIso, beforeValues));
        run.setAfterSnapshot(snapshot("AFTER", contextId, modelId, model.getSchemaVersion(), model.getWorkflowVersion(),
                toState, status, Instant.now().toString(), afterValues));
        if (result.passed) {
            context.setSchemaVersion(model.getSchemaVersion());
            context.setWorkflowVersion(model.getWorkflowVersion());
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
        addSpan(spans, traceId, "persistence", persistenceStarted, "PREPARED",
                mapOf("contextCommitted", String.valueOf(result.passed), "commitBoundary", "repository.commit"));
        long responseStarted = System.nanoTime();
        addSpan(spans, traceId, "response", responseStarted, "PREPARED",
                mapOf("deliveryBoundary", "caller-observation"));
        long durationNs = Math.max(0L, System.nanoTime() - startedAt);
        long durationMs = Math.max(1L, durationNs / 1000000L);
        run.setDurationNs(durationNs);
        run.setDurationMs(durationMs);
        run.setTrace(trace(run, startedAtIso, spans, status, durationNs, durationMs));
        if (deferPersistence) {
            return run;
        }
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
        try {
            repository.markPersistenceCommitted(state, runId);
        } catch (RuntimeException markerFailure) {
            try {
                restoreState(repository.load());
            } catch (RuntimeException ignored) {
                // Preserve the durable result and return the marker state below.
            }
            RuntimeRun persisted = findRun(runId);
            if (persisted != null) {
                return persisted;
            }
            throw new IllegalStateException("baseline run committed but trace commit observation failed", markerFailure);
        }
        return run;
    }

    private Map<String, Object> comparisonResult(String comparisonId, String caseId,
                                                  RuntimeRun baseline, RuntimeRun flexible) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("comparisonId", comparisonId);
        result.put("caseId", caseId);
        result.put("comparable", baseline.getModelId().equals(flexible.getModelId())
                && baseline.getEvent().equals(flexible.getEvent())
                && baseline.getInputSha256() != null
                && baseline.getInputSha256().equals(flexible.getInputSha256()));
        result.put("baselineRun", baseline);
        result.put("flexibleRun", flexible);
        return result;
    }

    private RuntimeRun findComparisonRun(String comparisonId, String executionMode) {
        for (RuntimeRun run : state.getRuns()) {
            if (comparisonId.equals(run.getComparisonId()) && executionMode.equals(run.getExecutionMode())) {
                return run;
            }
        }
        return null;
    }

    private boolean sameComparisonRequest(String modelId, String event, String caseId, String inputSha256,
                                          RuntimeRun baseline, RuntimeRun flexible) {
        return modelId.equals(baseline.getModelId())
                && modelId.equals(flexible.getModelId())
                && event.equals(baseline.getEvent())
                && event.equals(flexible.getEvent())
                && caseId.equals(baseline.getCaseId())
                && caseId.equals(flexible.getCaseId())
                && inputSha256.equals(baseline.getInputSha256())
                && inputSha256.equals(flexible.getInputSha256());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> copyPayload(Map<String, Object> payload) {
        if (payload == null) {
            throw new IllegalArgumentException("comparison payload must be an object");
        }
        return mapper.convertValue(payload, Map.class);
    }

    synchronized RuntimeRun replay(String runId) {
        RuntimeRun original = findRun(runId);
        if (original == null) {
            throw new IllegalArgumentException("run not found: " + runId);
        }
        if (original.getBeforeSnapshot() == null) {
            throw new IllegalArgumentException("run has no BEFORE snapshot: " + runId);
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("modelId", original.getModelId());
        payload.put("contextId", "replay-" + UUID.randomUUID().toString().substring(0, 8));
        payload.put("event", original.getEvent());
        payload.put("values", mapValue(original.getInputValues()));
        payload.put("ontology", copyValue(original.getOntologyInput()));
        payload.put("replayOfRunId", original.getId());
        payload.put("attempt", 1);
        payload.put("replayFromState", original.getBeforeSnapshot().getState());
        payload.put("replayBeforeStatus", original.getBeforeSnapshot().getStatus());
        payload.put("replayValues", mapValue(original.getBeforeSnapshot().getValues()));
        if (RigidMappingBaseline.MODE.equals(original.getExecutionMode())) {
            payload.put("executionMode", RigidMappingBaseline.MODE);
        }
        if (original.getOntologyVersion() > 0) {
            payload.put("expectedOntologyVersion", original.getOntologyVersion());
            payload.put("expectedOntologyDefinitionSha256", original.getOntologyDefinitionSha256());
        }
        return execute(payload);
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
        payload.put("values", mapValue(original.getInputValues()));
        payload.put("ontology", copyValue(original.getOntologyInput()));
        payload.put("retryOfRunId", original.getId());
        payload.put("attempt", original.getAttempt() + 1);
        if (RigidMappingBaseline.MODE.equals(original.getExecutionMode())) {
            payload.put("executionMode", RigidMappingBaseline.MODE);
        }
        if (original.getOntologyVersion() > 0) {
            payload.put("expectedOntologyVersion", original.getOntologyVersion());
            payload.put("expectedOntologyDefinitionSha256", original.getOntologyDefinitionSha256());
        }
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
        rollback.setExecutionMode(original.getExecutionMode());
        rollback.setOntologyTypeId(original.getOntologyTypeId());
        rollback.setOntologyVersion(original.getOntologyVersion());
        rollback.setOntologyDefinitionSha256(original.getOntologyDefinitionSha256());
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
        rollback.setReplayOfRunId(null);
        rollback.setAttempt(1);
        rollback.setContextRevision(context.getRevision() + 1L);
        rollback.setContextCommitted(true);
        rollback.setErrorCode(null);
        rollback.setInputValues(Collections.<String, Object>emptyMap());
        rollback.setValues(targetValues);
        rollback.setOntologyGraph(Collections.<String, Object>emptyMap());
        rollback.setOntologyInput(null);
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
        List<AuditChangeRecord> rollbackChanges = new ArrayList<AuditChangeRecord>();
        rollbackChanges.add(new AuditChangeRecord("context.state", beforeState, targetState));
        rollbackChanges.add(new AuditChangeRecord("context.status", beforeStatus, "ROLLED_BACK"));
        rollbackChanges.add(new AuditChangeRecord("context.values", copyValue(beforeValues), copyValue(targetValues)));
        state.getAuditEvents().add(0, new AuditEventRecord(
                "audit-" + UUID.randomUUID().toString().substring(0, 8), "RUN_ROLLED_BACK", "RuntimeRun",
                runId, Instant.now().toString(), "rollbackRunId=" + rollbackRunId,
                state.getRevision(), state.getRevision() + 1L, rollbackChanges));
        while (state.getAuditEvents().size() > 200) {
            state.getAuditEvents().remove(state.getAuditEvents().size() - 1);
        }

        long persistenceStarted = System.nanoTime();
        addSpan(spans, traceId, "persistence", persistenceStarted, "PREPARED",
                mapOf("contextCommitted", "true", "rollbackOfRunId", runId,
                        "commitBoundary", "repository.commit"));
        long responseStarted = System.nanoTime();
        addSpan(spans, traceId, "response", responseStarted, "OK", Collections.<String, String>emptyMap());
        long durationNs = Math.max(0L, System.nanoTime() - startedAt);
        long durationMs = Math.max(1L, durationNs / 1000000L);
        rollback.setDurationNs(durationNs);
        rollback.setDurationMs(durationMs);
        rollback.setTrace(trace(rollback, startedAtIso, spans, "ROLLED_BACK", durationNs, durationMs));

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
        repository.markPersistenceCommitted(state, rollbackRunId);
        return rollback;
    }

    private void restoreState(String stateJson) {
        try {
            EngineState restored = mapper.readValue(stateJson, EngineState.class);
            restoreState(restored);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot restore failed runtime transaction", exception);
        }
    }

    private void restoreState(EngineState restored) {
        if (restored == null) {
            throw new IllegalArgumentException("restored engine state must not be null");
        }
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
    }

    private Map<String, Object> invokeOntologyProvider(ServiceRegistration provider, String modelId,
                                                       String ontologyTypeId, String contextId,
                                                       Map<String, Object> values, Object input) {
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
        return ontologyProvider.assemble(modelId, ontologyTypeId, contextId, values, input, ontologyDefinitions());
    }

    private ServiceRegistration service(String serviceId) {
        for (ServiceRegistration candidate : state.getServices()) {
            if (serviceId.equals(candidate.getId())) {
                return candidate;
            }
        }
        return null;
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
            return mapValue(source);
        }
        Map<String, Object> migrated = mapValue(source);
        for (int version = fromVersion + 1; version <= model.getSchemaVersion(); version++) {
            SchemaVersionRecord targetSchema = schemaVersion(model, version);
            Map<String, Object> next = new LinkedHashMap<String, Object>();
            for (EngineField field : targetSchema.getFields()) {
                if (migrated.containsKey(field.getName())) {
                    next.put(field.getName(), copyValue(migrated.get(field.getName())));
                    continue;
                }
                SchemaMigrationRecord matchedRule = null;
                for (SchemaMigrationRecord rule : model.getSchemaMigrations()) {
                    if (rule.getFromVersion() == version - 1 && rule.getToVersion() == version
                            && field.getName().equals(rule.getTargetField())) {
                        if (matchedRule != null) {
                            throw new IllegalStateException("ambiguous schema migration for " + field.getName()
                                    + " at version " + version);
                        }
                        matchedRule = rule;
                    }
                }
                if (matchedRule != null && migrated.containsKey(matchedRule.getSourceField())) {
                    next.put(field.getName(), copyValue(migrated.get(matchedRule.getSourceField())));
                } else if (field.getDefaultValue() != null) {
                    next.put(field.getName(), copyValue(field.getDefaultValue()));
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
            return UnknownFieldPolicy.valueOf(model.getUnknownFieldPolicy().trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("model has an invalid unknownFieldPolicy: " + model.getId(), exception);
        }
    }

    private ExecutionSnapshotRecord snapshot(String phase, String contextId, String modelId, int schemaVersion,
                                             int workflowVersion, String state, String status, String capturedAt,
                                             Map<String, Object> values) {
        ExecutionStatus executionStatus;
        try {
            executionStatus = ExecutionStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("snapshot status is invalid: " + status, exception);
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

    private TraceRecord trace(RuntimeRun run, String startedAt, List<TraceSpanRecord> spans, String status,
                              long durationNs, long durationMs) {
        TraceRecord trace = new TraceRecord();
        trace.setRunId(run.getId());
        trace.setTraceId(run.getTraceId());
        trace.setStartedAt(startedAt);
        trace.setEndedAt(Instant.now().toString());
        trace.setDurationNs(durationNs);
        trace.setDurationMs(durationMs);
        trace.setStatus(status);
        trace.setLifecycle("PREPARED");
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
        long elapsedNanos = Math.max(0L, endedAt - startedAt);
        TraceSpanRecord span = new TraceSpanRecord("span-" + UUID.randomUUID().toString().substring(0, 8), traceId,
                name, startedAtIso.toString(), endedAtIso.toString(), elapsedNanos / 1000000L, status, attributes);
        span.setDurationNs(elapsedNanos);
        spans.add(span);
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot serialize provider evidence", exception);
        }
    }

    private Object copyValue(Object value) {
        return value == null ? null : mapper.convertValue(value, Object.class);
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

    private OntologyTypeConfig ontologyType(String typeId) {
        for (OntologyTypeConfig type : state.getOntologyTypes()) {
            if (typeId.equals(type.getId())) {
                return type;
            }
        }
        throw new IllegalStateException("ontology type not found: " + typeId);
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

    private String requestSha256(String modelId, String contextId, String event, Map<String, Object> values,
                                 Object ontology) {
        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put("modelId", modelId);
        request.put("contextId", contextId);
        request.put("event", event);
        request.put("values", values);
        request.put("ontology", copyValue(ontology));
        return new cn.finalartical.reproduction.flexible.ContextSnapshot(request).getSha256();
    }

    private String comparisonInputSha256(String modelId, String event, Map<String, Object> values,
                                         Object ontology) {
        Map<String, Object> input = new LinkedHashMap<String, Object>();
        input.put("modelId", modelId);
        input.put("event", event);
        input.put("values", values);
        input.put("ontology", copyValue(ontology));
        return new cn.finalartical.reproduction.flexible.ContextSnapshot(input).getSha256();
    }

    private String fixedBaselineConfigurationSha256(String modelId) {
        Map<String, Object> configuration = new LinkedHashMap<String, Object>();
        configuration.put("implementation", "RigidMappingBaseline");
        configuration.put("version", "1");
        configuration.put("modelId", modelId);
        return new cn.finalartical.reproduction.flexible.ContextSnapshot(configuration).getSha256();
    }

    private String flexibleConfigurationSha256(EngineModel model, int ontologyVersion,
                                               String ontologyDefinitionSha256) {
        Map<String, Object> configuration = new LinkedHashMap<String, Object>();
        configuration.put("implementation", "FlexibleEngine");
        configuration.put("engineVersion", state.getEngineVersion());
        configuration.put("modelId", model.getId());
        configuration.put("schemaVersion", model.getSchemaVersion());
        configuration.put("workflowVersion", model.getWorkflowVersion());
        configuration.put("schema", schemaIdentity(currentSchema(model)));
        configuration.put("workflow", workflowIdentity(workflow(model, model.getWorkflowVersion())));
        configuration.put("ontologyTypeId", model.getOntologyTypeId());
        configuration.put("ontologyVersion", ontologyVersion);
        configuration.put("ontologyDefinitionSha256", ontologyDefinitionSha256);
        return new cn.finalartical.reproduction.flexible.ContextSnapshot(configuration).getSha256();
    }

    private Map<String, Object> schemaIdentity(SchemaVersionRecord schema) {
        Map<String, Object> identity = new LinkedHashMap<String, Object>();
        identity.put("version", schema.getVersion());
        List<Map<String, Object>> fields = new ArrayList<Map<String, Object>>();
        for (EngineField field : schema.getFields()) {
            Map<String, Object> fieldIdentity = new LinkedHashMap<String, Object>();
            fieldIdentity.put("name", field.getName());
            fieldIdentity.put("type", field.getType());
            fieldIdentity.put("required", field.isRequired());
            fieldIdentity.put("version", field.getVersion());
            fieldIdentity.put("defaultValue", copyValue(field.getDefaultValue()));
            fields.add(fieldIdentity);
        }
        identity.put("fields", fields);
        return identity;
    }

    private Map<String, Object> workflowIdentity(WorkflowDefinition definition) {
        Map<String, Object> identity = new LinkedHashMap<String, Object>();
        identity.put("initialState", definition.getInitialState());
        List<Map<String, Object>> transitions = new ArrayList<Map<String, Object>>();
        for (WorkflowTransition transition : definition.getTransitions()) {
            Map<String, Object> transitionIdentity = new LinkedHashMap<String, Object>();
            transitionIdentity.put("fromState", transition.getFromState());
            transitionIdentity.put("event", transition.getEvent());
            transitionIdentity.put("toState", transition.getToState());
            transitions.add(transitionIdentity);
        }
        identity.put("transitions", transitions);
        return identity;
    }

    private Map<String, Object> mapValue(Object value) {
        if (value == null) {
            return new LinkedHashMap<String, Object>();
        }
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("values must be an object");
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            if (!(entry.getKey() instanceof String) || ((String) entry.getKey()).trim().isEmpty()) {
                throw new IllegalArgumentException("values object keys must be non-blank strings");
            }
            result.put((String) entry.getKey(), copyValue(entry.getValue()));
        }
        return result;
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

    private static int positiveIntValue(String value, String field) {
        try {
            int result = Integer.parseInt(value);
            if (result < 1) {
                throw new IllegalArgumentException(field + " must be positive");
            }
            return result;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be an integer");
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
