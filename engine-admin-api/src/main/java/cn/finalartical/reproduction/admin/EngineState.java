package cn.finalartical.reproduction.admin;

import java.util.ArrayList;
import java.util.List;

public class EngineState {
    private String engineId;
    private String engineName;
    private String engineVersion;
    private String updatedAt;
    private long revision;
    private List<EngineModel> models = new ArrayList<EngineModel>();
    private List<OntologyTypeConfig> ontologyTypes = new ArrayList<OntologyTypeConfig>();
    private List<ServiceRegistration> services = new ArrayList<ServiceRegistration>();
    private List<RuntimeRun> runs = new ArrayList<RuntimeRun>();
    private List<RuntimeContextRecord> contexts = new ArrayList<RuntimeContextRecord>();
    private List<AuditEventRecord> auditEvents = new ArrayList<AuditEventRecord>();
    private List<IdempotencyRecord> idempotencyRecords = new ArrayList<IdempotencyRecord>();

    public String getEngineId() {
        return engineId;
    }

    public void setEngineId(String engineId) {
        this.engineId = engineId;
    }

    public String getEngineName() {
        return engineName;
    }

    public void setEngineName(String engineName) {
        this.engineName = engineName;
    }

    public String getEngineVersion() {
        return engineVersion;
    }

    public void setEngineVersion(String engineVersion) {
        this.engineVersion = engineVersion;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getRevision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = revision;
    }

    public List<EngineModel> getModels() {
        return models;
    }

    public void setModels(List<EngineModel> models) {
        this.models = models == null ? new ArrayList<EngineModel>() : models;
    }

    public List<OntologyTypeConfig> getOntologyTypes() {
        return ontologyTypes;
    }

    public void setOntologyTypes(List<OntologyTypeConfig> ontologyTypes) {
        this.ontologyTypes = ontologyTypes == null ? new ArrayList<OntologyTypeConfig>() : ontologyTypes;
    }

    public List<ServiceRegistration> getServices() {
        return services;
    }

    public void setServices(List<ServiceRegistration> services) {
        this.services = services == null ? new ArrayList<ServiceRegistration>() : services;
    }

    public List<RuntimeRun> getRuns() {
        return runs;
    }

    public void setRuns(List<RuntimeRun> runs) {
        this.runs = runs == null ? new ArrayList<RuntimeRun>() : runs;
    }

    public List<RuntimeContextRecord> getContexts() {
        return contexts;
    }

    public void setContexts(List<RuntimeContextRecord> contexts) {
        this.contexts = contexts == null ? new ArrayList<RuntimeContextRecord>() : contexts;
    }

    public List<AuditEventRecord> getAuditEvents() {
        return auditEvents;
    }

    public void setAuditEvents(List<AuditEventRecord> auditEvents) {
        this.auditEvents = auditEvents == null ? new ArrayList<AuditEventRecord>() : auditEvents;
    }

    public List<IdempotencyRecord> getIdempotencyRecords() {
        return idempotencyRecords;
    }

    public void setIdempotencyRecords(List<IdempotencyRecord> idempotencyRecords) {
        this.idempotencyRecords = idempotencyRecords == null ? new ArrayList<IdempotencyRecord>() : idempotencyRecords;
    }
}
