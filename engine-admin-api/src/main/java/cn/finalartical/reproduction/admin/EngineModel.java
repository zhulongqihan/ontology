package cn.finalartical.reproduction.admin;

import java.util.ArrayList;
import java.util.List;

public class EngineModel {
    private String id;
    private String name;
    private String description;
    private int schemaVersion;
    private String initialState;
    private String updatedAt;
    private List<EngineField> fields = new ArrayList<EngineField>();
    private List<String> states = new ArrayList<String>();
    private List<EngineTransition> transitions = new ArrayList<EngineTransition>();

    public EngineModel() {
    }

    public EngineModel(String id, String name, String description, int schemaVersion, String initialState) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.schemaVersion = schemaVersion;
        this.initialState = initialState;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getInitialState() {
        return initialState;
    }

    public void setInitialState(String initialState) {
        this.initialState = initialState;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<EngineField> getFields() {
        return fields;
    }

    public void setFields(List<EngineField> fields) {
        this.fields = fields == null ? new ArrayList<EngineField>() : fields;
    }

    public List<String> getStates() {
        return states;
    }

    public void setStates(List<String> states) {
        this.states = states == null ? new ArrayList<String>() : states;
    }

    public List<EngineTransition> getTransitions() {
        return transitions;
    }

    public void setTransitions(List<EngineTransition> transitions) {
        this.transitions = transitions == null ? new ArrayList<EngineTransition>() : transitions;
    }
}
