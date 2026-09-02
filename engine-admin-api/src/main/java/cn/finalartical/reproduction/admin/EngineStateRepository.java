package cn.finalartical.reproduction.admin;

public interface EngineStateRepository {
    EngineState load();

    void save(EngineState state);
}
