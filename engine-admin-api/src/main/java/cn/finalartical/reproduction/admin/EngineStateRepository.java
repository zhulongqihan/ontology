package cn.finalartical.reproduction.admin;

public interface EngineStateRepository {
    EngineState load();

    void save(EngineState state);

    default void save(EngineState state, long expectedRevision) {
        save(state);
    }
}
