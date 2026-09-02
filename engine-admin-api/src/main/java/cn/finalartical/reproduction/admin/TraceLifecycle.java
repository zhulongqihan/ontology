package cn.finalartical.reproduction.admin;

import java.time.Instant;

/** Applies the repository commit observation after the state transaction returns. */
public final class TraceLifecycle {
    private TraceLifecycle() {
    }

    public static void markPersistenceCommitted(EngineState state, String runId) {
        if (state == null || state.getRuns() == null) {
            return;
        }
        String committedAt = Instant.now().toString();
        for (RuntimeRun run : state.getRuns()) {
            if (run == null || run.getTrace() == null || (runId != null && !runId.equals(run.getId()))) {
                continue;
            }
            TraceRecord trace = run.getTrace();
            if (!"PREPARED".equals(trace.getLifecycle())) {
                continue;
            }
            trace.setLifecycle("COMMITTED");
            for (TraceSpanRecord span : trace.getSpans()) {
                if ("persistence".equals(span.getName())) {
                    span.setStatus("COMMITTED");
                    span.getAttributes().put("commitObservedAt", committedAt);
                    span.getAttributes().put("commitRevision", String.valueOf(state.getRevision()));
                }
            }
        }
    }
}
