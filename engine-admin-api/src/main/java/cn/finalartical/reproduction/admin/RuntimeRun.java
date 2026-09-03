package cn.finalartical.reproduction.admin;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RuntimeRun {
    private String id;
    private String modelId;
    private String executionMode = "FLEXIBLE_ENGINE";
    private String comparisonId;
    private String pairedRunId;
    private String caseId;
    private String inputSha256;
    private String configurationSha256;
    private String ontologyTypeId;
    private int ontologyVersion;
    private String ontologyDefinitionSha256;
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
    private long durationNs;
    private long durationMs;
    private String idempotencyKey;
    private String retryOfRunId;
    private String replayOfRunId;
    private int attempt = 1;
    private long contextRevision;
    private boolean contextCommitted;
    private String errorCode;
    private ExecutionSnapshotRecord beforeSnapshot;
    private ExecutionSnapshotRecord afterSnapshot;
    private TraceRecord trace;
    private Map<String, Object> values = new LinkedHashMap<String, Object>();
    private Map<String, Object> inputValues = new LinkedHashMap<String, Object>();
    private Map<String, Object> ontologyGraph = new LinkedHashMap<String, Object>();
    private Object ontologyInput;
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

    public String getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode == null || executionMode.trim().isEmpty()
                ? "FLEXIBLE_ENGINE" : executionMode;
    }

    public String getComparisonId() {
        return comparisonId;
    }

    public void setComparisonId(String comparisonId) {
        this.comparisonId = comparisonId;
    }

    public String getPairedRunId() {
        return pairedRunId;
    }

    public void setPairedRunId(String pairedRunId) {
        this.pairedRunId = pairedRunId;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getInputSha256() {
        return inputSha256;
    }

    public void setInputSha256(String inputSha256) {
        this.inputSha256 = inputSha256;
    }

    public String getConfigurationSha256() {
        return configurationSha256;
    }

    public void setConfigurationSha256(String configurationSha256) {
        this.configurationSha256 = configurationSha256;
    }

    public String getOntologyTypeId() {
        return ontologyTypeId;
    }

    public void setOntologyTypeId(String ontologyTypeId) {
        this.ontologyTypeId = ontologyTypeId;
    }

    public int getOntologyVersion() {
        return ontologyVersion;
    }

    public void setOntologyVersion(int ontologyVersion) {
        this.ontologyVersion = ontologyVersion;
    }

    public String getOntologyDefinitionSha256() {
        return ontologyDefinitionSha256;
    }

    public void setOntologyDefinitionSha256(String ontologyDefinitionSha256) {
        this.ontologyDefinitionSha256 = ontologyDefinitionSha256;
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

    public long getDurationNs() {
        return durationNs;
    }

    public void setDurationNs(long durationNs) {
        this.durationNs = Math.max(0L, durationNs);
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRetryOfRunId() {
        return retryOfRunId;
    }

    public void setRetryOfRunId(String retryOfRunId) {
        this.retryOfRunId = retryOfRunId;
    }

    public String getReplayOfRunId() {
        return replayOfRunId;
    }

    public void setReplayOfRunId(String replayOfRunId) {
        this.replayOfRunId = replayOfRunId;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
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

    public Map<String, Object> getInputValues() {
        return inputValues;
    }

    public void setInputValues(Map<String, Object> inputValues) {
        this.inputValues = inputValues == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(inputValues);
    }

    public Map<String, Object> getOntologyGraph() {
        return ontologyGraph;
    }

    public void setOntologyGraph(Map<String, Object> ontologyGraph) {
        this.ontologyGraph = ontologyGraph == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(ontologyGraph);
    }

    public Object getOntologyInput() {
        return ontologyInput;
    }

    public void setOntologyInput(Object ontologyInput) {
        this.ontologyInput = ontologyInput;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<String> validationErrors) {
        this.validationErrors = validationErrors == null ? new ArrayList<String>() : new ArrayList<String>(validationErrors);
    }
}
