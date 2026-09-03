package cn.finalartical.reproduction.admin;

import java.util.LinkedHashMap;
import java.util.Map;

public class TraceSpanRecord {
    private String spanId;
    private String traceId;
    private String name;
    private String startedAt;
    private String endedAt;
    private long durationNs;
    private long durationMs;
    private String status;
    private Map<String, String> attributes = new LinkedHashMap<String, String>();

    public TraceSpanRecord() {
    }

    public TraceSpanRecord(String spanId, String traceId, String name, String startedAt, String endedAt,
                           long durationMs, String status, Map<String, String> attributes) {
        this.spanId = spanId;
        this.traceId = traceId;
        this.name = name;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.durationMs = durationMs;
        this.status = status;
        if (attributes != null) {
            this.attributes.putAll(attributes);
        }
    }

    public String getSpanId() { return spanId; }
    public void setSpanId(String spanId) { this.spanId = spanId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    public String getEndedAt() { return endedAt; }
    public void setEndedAt(String endedAt) { this.endedAt = endedAt; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public long getDurationNs() { return durationNs; }
    public void setDurationNs(long durationNs) { this.durationNs = Math.max(0L, durationNs); }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Map<String, String> getAttributes() { return attributes; }
    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes == null ? new LinkedHashMap<String, String>() : new LinkedHashMap<String, String>(attributes);
    }
}
