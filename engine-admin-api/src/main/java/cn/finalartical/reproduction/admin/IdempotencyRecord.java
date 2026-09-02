package cn.finalartical.reproduction.admin;

public class IdempotencyRecord {
    private String scope;
    private String key;
    private String requestSha256;
    private String runId;
    private String createdAt;

    public IdempotencyRecord() {
    }

    public IdempotencyRecord(String scope, String key, String requestSha256, String runId, String createdAt) {
        this.scope = scope;
        this.key = key;
        this.requestSha256 = requestSha256;
        this.runId = runId;
        this.createdAt = createdAt;
    }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getRequestSha256() { return requestSha256; }
    public void setRequestSha256(String requestSha256) { this.requestSha256 = requestSha256; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
