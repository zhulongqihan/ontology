package cn.finalartical.reproduction.admin;

public interface EngineStateRepository {
    EngineState load();

    void save(EngineState state);

    default void save(EngineState state, long expectedRevision) {
        save(state);
    }

    /** Marks one already persisted trace after the repository commit returns. */
    default void markPersistenceCommitted(EngineState state, String runId) {
        TraceLifecycle.markPersistenceCommitted(state, runId);
    }
}
