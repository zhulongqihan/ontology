package cn.finalartical.reproduction.admin;

import java.time.Instant;

/** Applies the repository commit observation to a trace before it is serialized. */
public final class TraceLifecycle {
    private TraceLifecycle() {
    }

    public static void markPersistenceCommitted(EngineState state) {
        if (state == null || state.getRuns() == null) {
            return;
        }
        String committedAt = Instant.now().toString();
        for (RuntimeRun run : state.getRuns()) {
            if (run == null || run.getTrace() == null) {
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
