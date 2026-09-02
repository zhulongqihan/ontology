package cn.finalartical.reproduction.admin;

import java.util.ArrayList;
import java.util.List;

public class WorkflowVersionRecord {
    private int version;
    private String publishedAt;
    private String initialState;
    private List<EngineTransition> transitions = new ArrayList<EngineTransition>();

    public WorkflowVersionRecord() {
    }

    public WorkflowVersionRecord(int version, String publishedAt, String initialState, List<EngineTransition> transitions) {
        this.version = version;
        this.publishedAt = publishedAt;
        this.initialState = initialState;
        setTransitions(transitions);
    }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getPublishedAt() { return publishedAt; }
    public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }
    public String getInitialState() { return initialState; }
    public void setInitialState(String initialState) { this.initialState = initialState; }
    public List<EngineTransition> getTransitions() { return transitions; }
    public void setTransitions(List<EngineTransition> transitions) {
        this.transitions = transitions == null ? new ArrayList<EngineTransition>() : new ArrayList<EngineTransition>(transitions);
    }
}
