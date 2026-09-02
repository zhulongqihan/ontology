package cn.finalartical.reproduction.admin;

import java.util.LinkedHashMap;
import java.util.Map;

public class RuntimeContextRecord {
    private String contextId;
    private String modelId;
    private int schemaVersion;
    private int workflowVersion;
    private String state;
    private String status;
    private long revision;
    private String lastRunId;
    private String lastSnapshotSha256;
    private String updatedAt;
    private Map<String, Object> values = new LinkedHashMap<String, Object>();

    public RuntimeContextRecord() {
    }

    public RuntimeContextRecord(String contextId, String modelId, int schemaVersion, int workflowVersion,
                                String state, String status, long revision, String updatedAt) {
        this.contextId = contextId;
        this.modelId = modelId;
        this.schemaVersion = schemaVersion;
        this.workflowVersion = workflowVersion;
        this.state = state;
        this.status = status;
        this.revision = revision;
        this.updatedAt = updatedAt;
    }

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
    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = revision; }
    public String getLastRunId() { return lastRunId; }
    public void setLastRunId(String lastRunId) { this.lastRunId = lastRunId; }
    public String getLastSnapshotSha256() { return lastSnapshotSha256; }
    public void setLastSnapshotSha256(String lastSnapshotSha256) { this.lastSnapshotSha256 = lastSnapshotSha256; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public Map<String, Object> getValues() { return values; }
    public void setValues(Map<String, Object> values) {
        this.values = values == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(values);
    }
}
