package cn.finalartical.reproduction.flexible;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TraceSpan {
    private final String spanId;
    private final String traceId;
    private final String name;
    private final String startedAt;
    private final String endedAt;
    private final long durationMs;
    private final String status;
    private final Map<String, String> attributes;

    public TraceSpan(String spanId, String traceId, String name, String startedAt, String endedAt,
                     long durationMs, String status, Map<String, String> attributes) {
        if (isBlank(spanId) || isBlank(traceId) || isBlank(name) || isBlank(startedAt)
                || isBlank(endedAt) || durationMs < 0 || isBlank(status)) {
            throw new IllegalArgumentException("trace span values must be valid");
        }
        this.spanId = spanId;
        this.traceId = traceId;
        this.name = name;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.durationMs = durationMs;
        this.status = status;
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<String, String>(
                attributes == null ? Collections.<String, String>emptyMap() : attributes));
    }

    public String getSpanId() { return spanId; }
    public String getTraceId() { return traceId; }
    public String getName() { return name; }
    public String getStartedAt() { return startedAt; }
    public String getEndedAt() { return endedAt; }
    public long getDurationMs() { return durationMs; }
    public String getStatus() { return status; }
    public Map<String, String> getAttributes() { return attributes; }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
