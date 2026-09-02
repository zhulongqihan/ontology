package cn.finalartical.reproduction.flexible;

public final class WorkflowVersion {
    private final int version;
    private final WorkflowDefinition definition;

    public WorkflowVersion(int version, WorkflowDefinition definition) {
        if (version < 1 || definition == null) {
            throw new IllegalArgumentException("workflow version values must be valid");
        }
        this.version = version;
        this.definition = definition;
    }

    public int getVersion() {
        return version;
    }

    public WorkflowDefinition getDefinition() {
        return definition;
    }
}
