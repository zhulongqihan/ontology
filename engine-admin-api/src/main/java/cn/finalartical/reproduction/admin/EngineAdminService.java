package cn.finalartical.reproduction.admin;

import cn.finalartical.reproduction.flexible.FieldType;
import cn.finalartical.reproduction.ontology.OntologyCardinality;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EngineAdminService {
    public static final String DATA_IDENTITY = "ENGINE_RUNTIME_RESULT";
    private static final String LEGACY_DATA_IDENTITY = "REPRODUCED_SYSTEM_RUN";
    private static final String LEGACY_ENGINE_ID = "flexible-engine-reproduction";
    private static final String LEGACY_ENGINE_NAME = "柔性引擎复现实例";
    private static final String LEGACY_ENGINE_VERSION = "0.2.0";
    private static final String ENGINE_ID = "flexible-engine-ontology";
    private static final String ENGINE_NAME = "柔性引擎与本体化平台";
    private static final String ENGINE_VERSION = "0.3.0";

    private final EngineStateRepository repository;
    private final EngineState state;
    private final EngineRuntimeService runtimeService;

    public EngineAdminService(EngineStateRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        this.repository = repository;
        this.state = repository.load();
        boolean changed = migrateProductIdentity();
        changed = normalizeModelVersions() || changed;
        changed = normalizeOntologyTypes() || changed;
        changed = normalizeContexts() || changed;
        if (changed) {
            touch(state);
            save();
        }
        this.runtimeService = new EngineRuntimeService(repository, state);
    }

    private boolean migrateProductIdentity() {
        boolean changed = false;
        if (LEGACY_ENGINE_ID.equals(state.getEngineId())) {
            state.setEngineId(ENGINE_ID);
            changed = true;
        }
        if (LEGACY_ENGINE_NAME.equals(state.getEngineName())) {
            state.setEngineName(ENGINE_NAME);
            changed = true;
        }
        if (LEGACY_ENGINE_VERSION.equals(state.getEngineVersion())) {
            state.setEngineVersion(ENGINE_VERSION);
            changed = true;
        }
        for (RuntimeRun run : state.getRuns()) {
            if (LEGACY_DATA_IDENTITY.equals(run.getDataIdentity())) {
                run.setDataIdentity(DATA_IDENTITY);
                changed = true;
            }
        }
        return changed;
    }

    public synchronized Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        Map<String, Object> engine = new LinkedHashMap<String, Object>();
        engine.put("id", state.getEngineId());
        engine.put("name", state.getEngineName());
        engine.put("version", state.getEngineVersion());
        engine.put("updatedAt", state.getUpdatedAt());
        engine.put("dataIdentity", DATA_IDENTITY);
        Map<String, Object> counts = new LinkedHashMap<String, Object>();
        counts.put("models", state.getModels().size());
        counts.put("fields", countFields());
        counts.put("ontologyTypes", state.getOntologyTypes().size());
        counts.put("services", state.getServices().size());
        counts.put("runs", state.getRuns().size());
        result.put("engine", engine);
        result.put("counts", counts);
        result.put("models", state.getModels());
        result.put("recentRuns", recentRuns(5));
        result.put("capabilities", Arrays.asList(
                "dynamic-schema", "workflow-runtime", "ontology-assembly", "local-provider"));
        return result;
    }

    public synchronized List<EngineModel> models() {
        return new ArrayList<EngineModel>(state.getModels());
    }

    public synchronized EngineModel addModel(Map<String, Object> payload) {
        String id = requiredText(payload, "id");
        for (EngineModel existing : state.getModels()) {
            if (id.equals(existing.getId())) {
                throw new IllegalArgumentException("model already exists: " + id);
            }
        }
        String initialState = textValue(payload.get("initialState"), "DRAFT").trim();
        if (initialState.isEmpty()) {
            initialState = "DRAFT";
        }
        EngineModel model = new EngineModel(id, requiredText(payload, "name"),
                textValue(payload.get("description"), ""), 1, initialState);
        model.getStates().add(initialState);
        model.setUpdatedAt(Instant.now().toString());
        state.getModels().add(model);
        appendAudit("MODEL_REGISTERED", "EngineModel", id, "model registered");
        touch(state);
        save();
        return model;
    }

    public synchronized EngineModel model(String modelId) {
        for (EngineModel model : state.getModels()) {
            if (model.getId().equals(modelId)) {
                return model;
            }
        }
        throw new IllegalArgumentException("model not found: " + modelId);
    }

    public synchronized EngineField addField(String modelId, Map<String, Object> payload) {
        EngineModel model = model(modelId);
        String name = requiredText(payload, "name");
        String type = requiredText(payload, "type").toUpperCase();
        try {
            FieldType.valueOf(type);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported field type: " + type);
        }
        for (EngineField field : model.getFields()) {
            if (name.equals(field.getName())) {
                throw new IllegalArgumentException("field already exists: " + name);
            }
        }
        int version = model.getSchemaVersion() + 1;
        EngineField field = new EngineField(name, type, booleanValue(payload.get("required"), false),
                version, payload.get("defaultValue"));
        model.getFields().add(field);
        model.setSchemaVersion(version);
        model.getSchemaVersions().add(new SchemaVersionRecord(version, Instant.now().toString(), copyFields(model.getFields())));
        appendAudit("SCHEMA_PUBLISHED", "SchemaVersion", modelId + ":v" + version, "field added: " + name);
        touch(model);
        save();
        return field;
    }

    public synchronized EngineTransition addTransition(String modelId, Map<String, Object> payload) {
        EngineModel model = model(modelId);
        String fromState = requiredText(payload, "fromState");
        String event = requiredText(payload, "event");
        String toState = requiredText(payload, "toState");
        for (EngineTransition transition : model.getTransitions()) {
            if (fromState.equals(transition.getFromState()) && event.equals(transition.getEvent())) {
                throw new IllegalArgumentException("transition already exists: " + fromState + " / " + event);
            }
        }
        if (!model.getStates().contains(fromState)) {
            model.getStates().add(fromState);
        }
        if (!model.getStates().contains(toState)) {
            model.getStates().add(toState);
        }
        EngineTransition transition = new EngineTransition(fromState, event, toState);
        model.getTransitions().add(transition);
        model.setWorkflowVersion(model.getWorkflowVersion() < 1 ? 1 : model.getWorkflowVersion() + 1);
        model.getWorkflowVersions().add(new WorkflowVersionRecord(model.getWorkflowVersion(), Instant.now().toString(),
                model.getInitialState(), copyTransitions(model.getTransitions())));
        appendAudit("WORKFLOW_PUBLISHED", "WorkflowVersion", modelId + ":v" + model.getWorkflowVersion(),
                "transition added: " + event);
        touch(model);
        save();
        return transition;
    }

    public synchronized List<OntologyTypeConfig> ontologyTypes() {
        return new ArrayList<OntologyTypeConfig>(state.getOntologyTypes());
    }

    public synchronized OntologyTypeConfig addOntologyType(Map<String, Object> payload) {
        String id = requiredText(payload, "id");
        for (OntologyTypeConfig type : state.getOntologyTypes()) {
            if (id.equals(type.getId())) {
                throw new IllegalArgumentException("ontology type already exists: " + id);
            }
        }
        OntologyTypeConfig type = new OntologyTypeConfig(id, requiredText(payload, "label"),
                textValue(payload.get("description"), ""));
        type.setFixedAttributes(stringList(payload.get("fixedAttributes")));
        type.setDynamicAttributes(stringList(payload.get("dynamicAttributes")));
        state.getOntologyTypes().add(type);
        appendAudit("ONTOLOGY_TYPE_REGISTERED", "OntologyType", id, "ontology type registered");
        touch(state);
        save();
        return type;
    }

    public synchronized OntologyRelationConfig addOntologyRelation(String typeId, Map<String, Object> payload) {
        OntologyTypeConfig type = ontologyType(typeId);
        String name = requiredText(payload, "name");
        String targetType = requiredText(payload, "targetType");
        String cardinality = OntologyCardinality.parse(requiredText(payload, "cardinality")).getExpression();
        ontologyType(targetType);
        for (OntologyRelationConfig relation : type.getRelations()) {
            if (name.equals(relation.getName())) {
                throw new IllegalArgumentException("ontology relation already exists: " + name);
            }
        }
        OntologyRelationConfig relation = new OntologyRelationConfig(name, targetType, cardinality);
        type.getRelations().add(relation);
        appendAudit("ONTOLOGY_RELATION_REGISTERED", "OntologyRelation", typeId + ":" + name,
                "target=" + targetType + ", cardinality=" + cardinality);
        touch(state);
        save();
        return relation;
    }

    public synchronized List<ServiceRegistration> services() {
        return new ArrayList<ServiceRegistration>(state.getServices());
    }

    public synchronized ServiceRegistration addService(Map<String, Object> payload) {
        String id = requiredText(payload, "id");
        for (ServiceRegistration existing : state.getServices()) {
            if (id.equals(existing.getId())) {
                throw new IllegalArgumentException("service already exists: " + id);
            }
        }
        ServiceRegistration service = new ServiceRegistration(
                id,
                requiredText(payload, "name"),
                requiredText(payload, "provider"),
                textValue(payload.get("status"), "READY"),
                requiredText(payload, "endpoint"),
                textValue(payload.get("version"), "v1"));
        state.getServices().add(service);
        appendAudit("SERVICE_REGISTERED", "Service", id, "endpoint=" + service.getEndpoint());
        touch(state);
        save();
        return service;
    }

    public synchronized List<RuntimeRun> runs() {
        return new ArrayList<RuntimeRun>(state.getRuns());
    }

    public synchronized RuntimeRun execute(Map<String, Object> payload) {
        return runtimeService.execute(payload);
    }

    public synchronized RuntimeRun retry(String runId) {
        return runtimeService.retry(runId);
    }

    public synchronized RuntimeRun rollback(String runId) {
        return runtimeService.rollback(runId);
    }

    private OntologyTypeConfig ontologyType(String typeId) {
        for (OntologyTypeConfig type : state.getOntologyTypes()) {
            if (type.getId().equals(typeId) || (type.getLabel() != null && type.getLabel().equalsIgnoreCase(typeId))) {
                return type;
            }
        }
        throw new IllegalArgumentException("ontology type not found: " + typeId);
    }

    public synchronized RuntimeContextRecord context(String contextId) {
        for (RuntimeContextRecord context : state.getContexts()) {
            if (contextId.equals(context.getContextId())) {
                return context;
            }
        }
        throw new IllegalArgumentException("context not found: " + contextId);
    }

    public synchronized List<RuntimeContextRecord> contexts() {
        return new ArrayList<RuntimeContextRecord>(state.getContexts());
    }

    public synchronized RuntimeRun run(String runId) {
        for (RuntimeRun run : state.getRuns()) {
            if (runId.equals(run.getId())) {
                return run;
            }
        }
        throw new IllegalArgumentException("run not found: " + runId);
    }

    public synchronized List<ExecutionSnapshotRecord> snapshots(String runId) {
        RuntimeRun run = run(runId);
        List<ExecutionSnapshotRecord> result = new ArrayList<ExecutionSnapshotRecord>();
        if (run.getBeforeSnapshot() != null) {
            result.add(run.getBeforeSnapshot());
        }
        if (run.getAfterSnapshot() != null) {
            result.add(run.getAfterSnapshot());
        }
        return result;
    }

    public synchronized List<IdempotencyRecord> idempotencyRecords() {
        return new ArrayList<IdempotencyRecord>(state.getIdempotencyRecords());
    }

    public synchronized Map<String, Object> exportState() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("exportedAt", Instant.now().toString());
        result.put("dataIdentity", DATA_IDENTITY);
        result.put("engine", overview().get("engine"));
        result.put("models", models());
        result.put("ontologyTypes", ontologyTypes());
        result.put("services", services());
        result.put("contexts", contexts());
        result.put("runs", runs());
        result.put("auditEvents", auditEvents());
        result.put("idempotencyRecords", idempotencyRecords());
        return result;
    }

    public synchronized List<AuditEventRecord> auditEvents() {
        return new ArrayList<AuditEventRecord>(state.getAuditEvents());
    }

    private boolean normalizeModelVersions() {
        boolean changed = false;
        for (EngineModel model : state.getModels()) {
            if (model.getSchemaVersion() < 1) {
                model.setSchemaVersion(1);
                changed = true;
            }
            if (model.getUnknownFieldPolicy() == null || model.getUnknownFieldPolicy().trim().isEmpty()) {
                model.setUnknownFieldPolicy("REJECT");
                changed = true;
            }
            if (model.getSchemaVersions().isEmpty()) {
                for (int version = 1; version <= model.getSchemaVersion(); version++) {
                    List<EngineField> fields = new ArrayList<EngineField>();
                    for (EngineField field : model.getFields()) {
                        if (field.getVersion() <= version) {
                            fields.add(copyField(field));
                        }
                    }
                    model.getSchemaVersions().add(new SchemaVersionRecord(version,
                            model.getUpdatedAt() == null ? Instant.now().toString() : model.getUpdatedAt(), fields));
                }
                changed = true;
            }
            if (model.getWorkflowVersion() < 1) {
                model.setWorkflowVersion(1);
                changed = true;
            }
            if (model.getWorkflowVersions().isEmpty()) {
                model.getWorkflowVersions().add(new WorkflowVersionRecord(model.getWorkflowVersion(),
                        model.getUpdatedAt() == null ? Instant.now().toString() : model.getUpdatedAt(),
                        model.getInitialState(), copyTransitions(model.getTransitions())));
                changed = true;
            }
        }
        return changed;
    }

    private boolean normalizeContexts() {
        boolean changed = false;
        for (RuntimeRun run : state.getRuns()) {
            if (!"PASSED".equals(run.getStatus()) || run.getContextId() == null || findContext(run.getContextId()) != null) {
                continue;
            }
            RuntimeContextRecord context = new RuntimeContextRecord(run.getContextId(), run.getModelId(),
                    run.getSchemaVersion() < 1 ? 1 : run.getSchemaVersion(),
                    run.getWorkflowVersion() < 1 ? 1 : run.getWorkflowVersion(), run.getToState(),
                    run.getStatus(), 1L, run.getCreatedAt());
            context.setValues(run.getValues());
            context.setLastRunId(run.getId());
            if (run.getAfterSnapshot() != null) {
                context.setLastSnapshotSha256(run.getAfterSnapshot().getSha256());
            }
            state.getContexts().add(context);
            changed = true;
        }
        return changed;
    }

    private boolean normalizeOntologyTypes() {
        boolean changed = false;
        for (OntologyTypeConfig type : state.getOntologyTypes()) {
            if ("questionnaire".equals(type.getId()) && !type.getDynamicAttributes().contains("subjects")) {
                type.getDynamicAttributes().add(0, "subjects");
                changed = true;
            }
            if ("subject".equals(type.getId()) && !type.getDynamicAttributes().contains("optionCount")) {
                type.getDynamicAttributes().add("optionCount");
                changed = true;
            }
            if ("option".equals(type.getId()) && !type.getFixedAttributes().contains("label")) {
                type.getFixedAttributes().add("label");
                changed = true;
            }
        }
        return changed;
    }

    private RuntimeContextRecord findContext(String contextId) {
        for (RuntimeContextRecord context : state.getContexts()) {
            if (contextId.equals(context.getContextId())) {
                return context;
            }
        }
        return null;
    }

    private void appendAudit(String action, String targetType, String targetId, String details) {
        state.getAuditEvents().add(0, new AuditEventRecord(
                "audit-" + UUID.randomUUID().toString().substring(0, 8), action, targetType, targetId,
                Instant.now().toString(), details));
        while (state.getAuditEvents().size() > 200) {
            state.getAuditEvents().remove(state.getAuditEvents().size() - 1);
        }
    }

    private static EngineField copyField(EngineField field) {
        return new EngineField(field.getName(), field.getType(), field.isRequired(), field.getVersion(), field.getDefaultValue());
    }

    private static List<EngineField> copyFields(List<EngineField> fields) {
        List<EngineField> result = new ArrayList<EngineField>();
        for (EngineField field : fields) {
            result.add(copyField(field));
        }
        return result;
    }

    private static List<EngineTransition> copyTransitions(List<EngineTransition> transitions) {
        List<EngineTransition> result = new ArrayList<EngineTransition>();
        for (EngineTransition transition : transitions) {
            result.add(new EngineTransition(transition.getFromState(), transition.getEvent(), transition.getToState()));
        }
        return result;
    }

    private int countFields() {
        int total = 0;
        for (EngineModel model : state.getModels()) {
            total += model.getFields().size();
        }
        return total;
    }

    private List<RuntimeRun> recentRuns(int limit) {
        return new ArrayList<RuntimeRun>(state.getRuns().subList(0, Math.min(limit, state.getRuns().size())));
    }

    private void touch(EngineModel model) {
        String now = Instant.now().toString();
        model.setUpdatedAt(now);
        touch(state);
    }

    private void touch(EngineState engineState) {
        engineState.setUpdatedAt(Instant.now().toString());
    }

    private void save() {
        try {
            repository.save(state, state.getRevision());
        } catch (RuntimeException exception) {
            try {
                restoreState(repository.load());
            } catch (RuntimeException ignored) {
                // Preserve the original write failure; the next service instance can still reload persisted state.
            }
            throw exception;
        }
    }

    private void restoreState(EngineState restored) {
        state.setEngineId(restored.getEngineId());
        state.setEngineName(restored.getEngineName());
        state.setEngineVersion(restored.getEngineVersion());
        state.setUpdatedAt(restored.getUpdatedAt());
        state.setRevision(restored.getRevision());
        state.setModels(restored.getModels());
        state.setOntologyTypes(restored.getOntologyTypes());
        state.setServices(restored.getServices());
        state.setRuns(restored.getRuns());
        state.setContexts(restored.getContexts());
        state.setAuditEvents(restored.getAuditEvents());
        state.setIdempotencyRecords(restored.getIdempotencyRecords());
    }

    private static String requiredText(Map<String, Object> payload, String key) {
        String value = payload == null ? "" : textValue(payload.get(key), "").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private static String textValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static List<String> stringList(Object value) {
        if (value == null) {
            return new ArrayList<String>();
        }
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("attribute list must be an array");
        }
        List<String> result = new ArrayList<String>();
        for (Object item : (List<?>) value) {
            result.add(String.valueOf(item));
        }
        return result;
    }
}
