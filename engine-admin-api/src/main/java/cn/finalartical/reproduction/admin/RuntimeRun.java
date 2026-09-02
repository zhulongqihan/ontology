package cn.finalartical.reproduction.admin;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RuntimeRun {
    private String id;
    private String modelId;
    private String contextId;
    private String engineVersion;
    private int schemaVersion;
    private int workflowVersion;
    private String status;
    private String dataIdentity;
    private String event;
    private String fromState;
    private String toState;
    private String traceId;
    private String createdAt;
    private long durationMs;
    private String idempotencyKey;
    private long contextRevision;
    private boolean contextCommitted;
    private String errorCode;
    private ExecutionSnapshotRecord beforeSnapshot;
    private ExecutionSnapshotRecord afterSnapshot;
    private TraceRecord trace;
    private Map<String, Object> values = new LinkedHashMap<String, Object>();
    private Map<String, Object> ontologyGraph = new LinkedHashMap<String, Object>();
    private List<String> validationErrors = new ArrayList<String>();

    public RuntimeRun() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getContextId() {
        return contextId;
    }

    public void setContextId(String contextId) {
        this.contextId = contextId;
    }

    public String getEngineVersion() {
        return engineVersion;
    }

    public void setEngineVersion(String engineVersion) {
        this.engineVersion = engineVersion;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public int getWorkflowVersion() {
        return workflowVersion;
    }

    public void setWorkflowVersion(int workflowVersion) {
        this.workflowVersion = workflowVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDataIdentity() {
        return dataIdentity;
    }

    public void setDataIdentity(String dataIdentity) {
        this.dataIdentity = dataIdentity;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getFromState() {
        return fromState;
    }

    public void setFromState(String fromState) {
        this.fromState = fromState;
    }

    public String getToState() {
        return toState;
    }

    public void setToState(String toState) {
        this.toState = toState;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public long getContextRevision() {
        return contextRevision;
    }

    public void setContextRevision(long contextRevision) {
        this.contextRevision = contextRevision;
    }

    public boolean isContextCommitted() {
        return contextCommitted;
    }

    public void setContextCommitted(boolean contextCommitted) {
        this.contextCommitted = contextCommitted;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public ExecutionSnapshotRecord getBeforeSnapshot() {
        return beforeSnapshot;
    }

    public void setBeforeSnapshot(ExecutionSnapshotRecord beforeSnapshot) {
        this.beforeSnapshot = beforeSnapshot;
    }

    public ExecutionSnapshotRecord getAfterSnapshot() {
        return afterSnapshot;
    }

    public void setAfterSnapshot(ExecutionSnapshotRecord afterSnapshot) {
        this.afterSnapshot = afterSnapshot;
    }

    public TraceRecord getTrace() {
        return trace;
    }

    public void setTrace(TraceRecord trace) {
        this.trace = trace;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public void setValues(Map<String, Object> values) {
        this.values = values == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(values);
    }

    public Map<String, Object> getOntologyGraph() {
        return ontologyGraph;
    }

    public void setOntologyGraph(Map<String, Object> ontologyGraph) {
        this.ontologyGraph = ontologyGraph == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(ontologyGraph);
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<String> validationErrors) {
        this.validationErrors = validationErrors == null ? new ArrayList<String>() : new ArrayList<String>(validationErrors);
    }
}
