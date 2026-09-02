package cn.finalartical.reproduction.admin;

import cn.finalartical.reproduction.flexible.FieldDefinition;
import cn.finalartical.reproduction.flexible.FieldType;
import cn.finalartical.reproduction.flexible.FlexibleEngine;
import cn.finalartical.reproduction.flexible.WorkflowDefinition;
import cn.finalartical.reproduction.flexible.WorkflowTransition;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    public EngineAdminService(EngineStateRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        this.repository = repository;
        this.state = repository.load();
        migrateProductIdentity();
    }

    private void migrateProductIdentity() {
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
        if (changed) {
            state.setUpdatedAt(Instant.now().toString());
            repository.save(state);
        }
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
        touch(state);
        save();
        return type;
    }

    public synchronized OntologyRelationConfig addOntologyRelation(String typeId, Map<String, Object> payload) {
        OntologyTypeConfig type = ontologyType(typeId);
        String name = requiredText(payload, "name");
        String targetType = requiredText(payload, "targetType");
        String cardinality = requiredText(payload, "cardinality");
        ontologyType(targetType);
        for (OntologyRelationConfig relation : type.getRelations()) {
            if (name.equals(relation.getName())) {
                throw new IllegalArgumentException("ontology relation already exists: " + name);
            }
        }
        OntologyRelationConfig relation = new OntologyRelationConfig(name, targetType, cardinality);
        type.getRelations().add(relation);
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
        touch(state);
        save();
        return service;
    }

    public synchronized List<RuntimeRun> runs() {
        return new ArrayList<RuntimeRun>(state.getRuns());
    }

    public synchronized RuntimeRun execute(Map<String, Object> payload) {
        long startedAt = System.nanoTime();
        String modelId = requiredText(payload, "modelId");
        EngineModel model = model(modelId);
        Map<String, Object> values = mapValue(payload.get("values"));
        String event = textValue(payload.get("event"), "");
        String contextId = textValue(payload.get("contextId"), "").trim();
        if (contextId.isEmpty()) {
            contextId = "ctx-" + UUID.randomUUID().toString().substring(0, 8);
        }
        RuntimeRun previous = latestRun(modelId, contextId);
        String runtimeState = previous == null ? model.getInitialState() : previous.getToState();
        List<FieldDefinition> definitions = new ArrayList<FieldDefinition>();
        for (EngineField field : model.getFields()) {
            definitions.add(new FieldDefinition(field.getName(), FieldType.valueOf(field.getType()),
                    field.isRequired(), field.getVersion()));
        }
        FlexibleEngine engine = new FlexibleEngine(definitions, workflow(model), runtimeState);
        for (EngineField field : model.getFields()) {
            if (field.getDefaultValue() != null) {
                engine.set(field.getName(), field.getDefaultValue());
            }
        }
        if (previous != null) {
            for (Map.Entry<String, Object> value : previous.getValues().entrySet()) {
                engine.set(value.getKey(), value.getValue());
            }
        }
        for (Map.Entry<String, Object> value : values.entrySet()) {
            engine.set(value.getKey(), value.getValue());
        }

        String fromState = engine.state();
        String toState = fromState;
        List<String> errors = new ArrayList<String>(engine.validate());
        if (errors.isEmpty()) {
            if (event.trim().isEmpty()) {
                errors.add("event is required");
            } else {
                try {
                    toState = engine.apply(event);
                } catch (IllegalStateException exception) {
                    errors.add(exception.getMessage());
                }
            }
        }

        RuntimeRun run = new RuntimeRun();
        run.setId("run-" + UUID.randomUUID().toString().substring(0, 8));
        run.setModelId(modelId);
        run.setContextId(contextId);
        run.setStatus(errors.isEmpty() ? "PASSED" : "FAILED");
        run.setDataIdentity(DATA_IDENTITY);
        run.setEvent(event);
        run.setFromState(fromState);
        run.setToState(toState);
        run.setTraceId("trace-" + run.getId());
        run.setCreatedAt(Instant.now().toString());
        run.setDurationMs(Math.max(1L, (System.nanoTime() - startedAt) / 1000000L));
        run.setValues(new LinkedHashMap<String, Object>(engine.values()));
        run.setValidationErrors(errors);
        state.getRuns().add(0, run);
        while (state.getRuns().size() > 50) {
            state.getRuns().remove(state.getRuns().size() - 1);
        }
        touch(state);
        save();
        return run;
    }

    private WorkflowDefinition workflow(EngineModel model) {
        List<WorkflowTransition> transitions = new ArrayList<WorkflowTransition>();
        for (EngineTransition transition : model.getTransitions()) {
            transitions.add(new WorkflowTransition(transition.getFromState(), transition.getEvent(), transition.getToState()));
        }
        return new WorkflowDefinition(model.getInitialState(), transitions);
    }

    private OntologyTypeConfig ontologyType(String typeId) {
        for (OntologyTypeConfig type : state.getOntologyTypes()) {
            if (type.getId().equals(typeId)) {
                return type;
            }
        }
        throw new IllegalArgumentException("ontology type not found: " + typeId);
    }

    private RuntimeRun latestRun(String modelId, String contextId) {
        for (RuntimeRun run : state.getRuns()) {
            if (modelId.equals(run.getModelId()) && contextId.equals(run.getContextId())) {
                return run;
            }
        }
        return null;
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
        repository.save(state);
    }

    private static String requiredText(Map<String, Object> payload, String key) {
        String value = textValue(payload.get(key), "").trim();
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (value == null) {
            return Collections.emptyMap();
        }
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("values must be an object");
        }
        return (Map<String, Object>) value;
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
