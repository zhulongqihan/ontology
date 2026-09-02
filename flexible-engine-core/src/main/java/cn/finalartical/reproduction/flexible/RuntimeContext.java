package cn.finalartical.reproduction.flexible;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RuntimeContext {
    private final String id;
    private final String modelId;
    private int schemaVersion;
    private int workflowVersion;
    private String state;
    private ExecutionStatus status;
    private long revision;
    private String lastRunId;
    private final Map<String, Object> values = new LinkedHashMap<String, Object>();

    public RuntimeContext(String id, String modelId, int schemaVersion, int workflowVersion, String initialState) {
        if (isBlank(id) || isBlank(modelId) || isBlank(initialState)) {
            throw new IllegalArgumentException("context identity and initial state must not be blank");
        }
        if (schemaVersion < 1 || workflowVersion < 1) {
            throw new IllegalArgumentException("context versions must be positive");
        }
        this.id = id;
        this.modelId = modelId;
        this.schemaVersion = schemaVersion;
        this.workflowVersion = workflowVersion;
        this.state = initialState;
        this.status = ExecutionStatus.CREATED;
    }

    public synchronized void apply(String nextState, Map<String, ?> nextValues, String runId, ExecutionStatus nextStatus) {
        if (isBlank(nextState) || isBlank(runId) || nextValues == null || nextStatus == null) {
            throw new IllegalArgumentException("context update must be valid");
        }
        state = nextState;
        values.clear();
        values.putAll(nextValues);
        lastRunId = runId;
        status = nextStatus;
        revision++;
    }

    public synchronized void updateVersions(int nextSchemaVersion, int nextWorkflowVersion) {
        if (nextSchemaVersion < 1 || nextWorkflowVersion < 1) {
            throw new IllegalArgumentException("context versions must be positive");
        }
        schemaVersion = nextSchemaVersion;
        workflowVersion = nextWorkflowVersion;
    }

    public synchronized ExecutionSnapshot snapshot() {
        return snapshot(Instant.now().toString());
    }

    public synchronized ExecutionSnapshot snapshot(String capturedAt) {
        return new ExecutionSnapshot(id, modelId, schemaVersion, workflowVersion, state, status, capturedAt, values);
    }

    public String getId() {
        return id;
    }

    public String getModelId() {
        return modelId;
    }

    public synchronized int getSchemaVersion() {
        return schemaVersion;
    }

    public synchronized int getWorkflowVersion() {
        return workflowVersion;
    }

    public synchronized String getState() {
        return state;
    }

    public synchronized ExecutionStatus getStatus() {
        return status;
    }

    public synchronized long getRevision() {
        return revision;
    }

    public synchronized String getLastRunId() {
        return lastRunId;
    }

    public synchronized Map<String, Object> getValues() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, Object>(values));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
