package cn.finalartical.reproduction.admin;

import java.util.ArrayList;
import java.util.List;

public class EngineModel {
    private String id;
    private String name;
    private String description;
    /** Explicit root ontology type. Null means this model is not ontology-backed. */
    private String ontologyTypeId;
    private int schemaVersion;
    private int workflowVersion = 1;
    private String initialState;
    private String updatedAt;
    private String unknownFieldPolicy = "REJECT";
    private List<EngineField> fields = new ArrayList<EngineField>();
    private List<String> states = new ArrayList<String>();
    private List<EngineTransition> transitions = new ArrayList<EngineTransition>();
    private List<SchemaVersionRecord> schemaVersions = new ArrayList<SchemaVersionRecord>();
    private List<SchemaMigrationRecord> schemaMigrations = new ArrayList<SchemaMigrationRecord>();
    private List<WorkflowVersionRecord> workflowVersions = new ArrayList<WorkflowVersionRecord>();

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

    public String getOntologyTypeId() {
        return ontologyTypeId;
    }

    public void setOntologyTypeId(String ontologyTypeId) {
        this.ontologyTypeId = ontologyTypeId == null || ontologyTypeId.trim().isEmpty()
                ? null : ontologyTypeId.trim();
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public int getWorkflowVersion() {
        return workflowVersion;
    }

    public void setWorkflowVersion(int workflowVersion) {
        this.workflowVersion = workflowVersion;
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

    public String getUnknownFieldPolicy() {
        return unknownFieldPolicy;
    }

    public void setUnknownFieldPolicy(String unknownFieldPolicy) {
        this.unknownFieldPolicy = unknownFieldPolicy == null ? "REJECT" : unknownFieldPolicy;
    }

    public List<EngineField> getFields() {
        return fields;
    }

    public void setFields(List<EngineField> fields) {
        this.fields = fields == null ? new ArrayList<EngineField>() : new ArrayList<EngineField>(fields);
    }

    public List<String> getStates() {
        return states;
    }

    public void setStates(List<String> states) {
        this.states = states == null ? new ArrayList<String>() : new ArrayList<String>(states);
    }

    public List<EngineTransition> getTransitions() {
        return transitions;
    }

    public void setTransitions(List<EngineTransition> transitions) {
        this.transitions = transitions == null ? new ArrayList<EngineTransition>() : new ArrayList<EngineTransition>(transitions);
    }

    public List<SchemaVersionRecord> getSchemaVersions() {
        return schemaVersions;
    }

    public void setSchemaVersions(List<SchemaVersionRecord> schemaVersions) {
        this.schemaVersions = schemaVersions == null ? new ArrayList<SchemaVersionRecord>() : schemaVersions;
    }

    public List<SchemaMigrationRecord> getSchemaMigrations() {
        return schemaMigrations;
    }

    public void setSchemaMigrations(List<SchemaMigrationRecord> schemaMigrations) {
        this.schemaMigrations = schemaMigrations == null ? new ArrayList<SchemaMigrationRecord>() : schemaMigrations;
    }

    public List<WorkflowVersionRecord> getWorkflowVersions() {
        return workflowVersions;
    }

    public void setWorkflowVersions(List<WorkflowVersionRecord> workflowVersions) {
        this.workflowVersions = workflowVersions == null ? new ArrayList<WorkflowVersionRecord>() : workflowVersions;
    }
}
