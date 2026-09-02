package cn.finalartical.reproduction.admin;

import java.util.LinkedHashMap;
import java.util.Map;

public class ExecutionSnapshotRecord {
    private String phase;
    private String contextId;
    private String modelId;
    private int schemaVersion;
    private int workflowVersion;
    private String state;
    private String status;
    private String capturedAt;
    private Map<String, Object> values = new LinkedHashMap<String, Object>();
    private String sha256;

    public ExecutionSnapshotRecord() {
    }

    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public String getContextId() { return contextId; }
    public void setContextId(String contextId) { this.contextId = contextId; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    public int getWorkflowVersion() { return workflowVersion; }
    public void setWorkflowVersion(int workflowVersion) { this.workflowVersion = workflowVersion; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCapturedAt() { return capturedAt; }
    public void setCapturedAt(String capturedAt) { this.capturedAt = capturedAt; }
    public Map<String, Object> getValues() { return values; }
    public void setValues(Map<String, Object> values) {
        this.values = values == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(values);
    }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
}
