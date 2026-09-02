package cn.finalartical.reproduction.flexible;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Trace {
    private final String runId;
    private final String traceId;
    private final String startedAt;
    private final List<TraceSpan> spans = new ArrayList<TraceSpan>();
    private boolean sealed;

    public Trace(String runId, String traceId) {
        if (isBlank(runId) || isBlank(traceId)) {
            throw new IllegalArgumentException("trace run id and trace id must not be blank");
        }
        this.runId = runId;
        this.traceId = traceId;
        this.startedAt = Instant.now().toString();
    }

    public synchronized Trace append(TraceSpan span) {
        if (sealed) {
            throw new IllegalStateException("trace is sealed");
        }
        if (span == null || !traceId.equals(span.getTraceId())) {
            throw new IllegalArgumentException("trace span must belong to trace");
        }
        spans.add(span);
        return this;
    }

    public synchronized void seal() {
        sealed = true;
    }

    public String getRunId() { return runId; }
    public String getTraceId() { return traceId; }
    public String getStartedAt() { return startedAt; }
    public synchronized List<TraceSpan> getSpans() {
        return Collections.unmodifiableList(new ArrayList<TraceSpan>(spans));
    }
    public synchronized boolean isSealed() { return sealed; }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
