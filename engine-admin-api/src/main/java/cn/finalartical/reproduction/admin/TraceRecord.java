package cn.finalartical.reproduction.admin;

import java.util.ArrayList;
import java.util.List;

public class TraceRecord {
    private String runId;
    private String traceId;
    private String startedAt;
    private String endedAt;
    private long durationMs;
    private String status;
    private String lifecycle;
    private boolean sealed;
    private List<TraceSpanRecord> spans = new ArrayList<TraceSpanRecord>();

    public TraceRecord() {
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    public String getEndedAt() { return endedAt; }
    public void setEndedAt(String endedAt) { this.endedAt = endedAt; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLifecycle() { return lifecycle; }
    public void setLifecycle(String lifecycle) { this.lifecycle = lifecycle; }
    public boolean isSealed() { return sealed; }
    public void setSealed(boolean sealed) { this.sealed = sealed; }
    public List<TraceSpanRecord> getSpans() { return spans; }
    public void setSpans(List<TraceSpanRecord> spans) {
        this.spans = spans == null ? new ArrayList<TraceSpanRecord>() : new ArrayList<TraceSpanRecord>(spans);
    }
}
