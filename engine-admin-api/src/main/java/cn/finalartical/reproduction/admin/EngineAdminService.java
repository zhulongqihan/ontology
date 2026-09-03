package cn.finalartical.reproduction.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.finalartical.reproduction.flexible.FieldDefinition;
import cn.finalartical.reproduction.flexible.FieldType;
import cn.finalartical.reproduction.flexible.UnknownFieldPolicy;
import cn.finalartical.reproduction.ontology.OntologyCardinality;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class EngineAdminService {
    public static final String DATA_IDENTITY = "ENGINE_RUNTIME_RESULT";
    public static final String LEGACY_RUNTIME_IDENTITY = "LEGACY_RUNTIME_RECORD";
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
    private final ObjectMapper mapper = new ObjectMapper();

    public EngineAdminService(EngineStateRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        this.repository = repository;
        this.state = repository.load();
        boolean changed = migrateProductIdentity();
        changed = normalizeModelVersions() || changed;
        changed = normalizeModelOntologyBindings() || changed;
        changed = normalizeOntologyTypes() || changed;
        changed = normalizeContexts() || changed;
        changed = normalizeServiceBindings() || changed;
        changed = normalizeLegacyRuns() || changed;
        validateModelContracts();
        validateAuditContracts();
        EvidenceIntegrity.validateState(state);
        if (changed) {
            touch(state);
            save();
        }
        this.runtimeService = new EngineRuntimeService(repository, state);
    }

    /**
     * Migrate the one historical exact-id convention into durable metadata.
     * This is deliberately a one-time load migration; runtime execution never
     * falls back to model names or ontology labels.
     */
    private boolean normalizeModelOntologyBindings() {
        boolean changed = false;
        for (EngineModel model : state.getModels()) {
            if (model.getOntologyTypeId() != null) {
                continue;
            }
            for (OntologyTypeConfig type : state.getOntologyTypes()) {
                if (model.getId().equals(type.getId())) {
                    model.setOntologyTypeId(type.getId());
                    changed = true;
                    break;
                }
            }
        }
        return changed;
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
        return changed;
    }

    private boolean normalizeServiceBindings() {
        boolean changed = false;
        for (ServiceRegistration service : state.getServices()) {
            if ("ontology-assembler".equals(service.getId())
                    && "OntologyAssembler".equals(service.getProvider())) {
                service.setProvider("LocalOntologyProvider");
                changed = true;
            }
        }
        return changed;
    }

    private boolean normalizeLegacyRuns() {
        boolean changed = false;
        for (RuntimeRun run : state.getRuns()) {
            if (run == null || LEGACY_DATA_IDENTITY.equals(run.getDataIdentity())
                    || LEGACY_RUNTIME_IDENTITY.equals(run.getDataIdentity())) {
                continue;
            }
            if (isBlank(run.getDataIdentity())) {
                run.setDataIdentity(LEGACY_RUNTIME_IDENTITY);
                changed = true;
            }
            if (isLegacyRuntimeRun(run)) {
                run.setDataIdentity(LEGACY_RUNTIME_IDENTITY);
                changed = true;
            }
            if (run.getTrace() != null && isBlank(run.getTrace().getLifecycle())) {
                run.getTrace().setLifecycle("LEGACY_UNKNOWN");
                changed = true;
            }
            if (EngineAdminService.DATA_IDENTITY.equals(run.getDataIdentity())
                    && run.getOntologyTypeId() != null
                    && (run.getOntologyVersion() < 1 || isBlank(run.getOntologyDefinitionSha256()))) {
                // The old format recorded only the type id. The exact definition
                // used at execution time cannot be reconstructed safely.
                run.setDataIdentity(LEGACY_RUNTIME_IDENTITY);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isLegacyRuntimeRun(RuntimeRun run) {
        return run.getEngineVersion() == null || run.getEngineVersion().trim().isEmpty()
                || run.getSchemaVersion() < 1 || run.getWorkflowVersion() < 1
                || run.getTrace() == null || run.getBeforeSnapshot() == null || run.getAfterSnapshot() == null;
    }

    public synchronized Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        Map<String, Object> engine = new LinkedHashMap<String, Object>();
        engine.put("id", state.getEngineId());
        engine.put("name", state.getEngineName());
        engine.put("version", state.getEngineVersion());
        engine.put("updatedAt", state.getUpdatedAt());
        engine.put("revision", state.getRevision());
        engine.put("dataIdentity", DATA_IDENTITY);
        Map<String, Object> counts = new LinkedHashMap<String, Object>();
        counts.put("models", state.getModels().size());
        counts.put("fields", countFields());
        counts.put("ontologyTypes", state.getOntologyTypes().size());
        counts.put("services", state.getServices().size());
        counts.put("runs", state.getRuns().size());
        result.put("engine", engine);
        result.put("counts", counts);
        result.put("models", models());
        result.put("recentRuns", recentRuns(5));
        result.put("capabilities", Arrays.asList(
                "dynamic-schema", "workflow-runtime", "ontology-assembly", "local-provider"));
        return result;
    }

    public synchronized long revision() {
        return state.getRevision();
    }

    public synchronized void requireRevision(long expectedRevision) {
        if (state.getRevision() != expectedRevision) {
            throw new ConcurrentModificationException("engine state revision conflict: expected "
                    + expectedRevision + " but was " + state.getRevision());
        }
    }

    public synchronized List<EngineModel> models() {
        List<EngineModel> result = new ArrayList<EngineModel>();
        for (EngineModel model : state.getModels()) {
            result.add(copy(model, EngineModel.class));
        }
        return result;
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
        String ontologyTypeId = optionalOntologyTypeId(payload, "ontologyTypeId");
        model.setOntologyTypeId(ontologyTypeId);
        model.getStates().add(initialState);
        String publishedAt = Instant.now().toString();
        model.setUpdatedAt(publishedAt);
        model.getSchemaVersions().add(new SchemaVersionRecord(1, publishedAt, new ArrayList<EngineField>()));
        model.getWorkflowVersions().add(new WorkflowVersionRecord(1, publishedAt, initialState,
                new ArrayList<EngineTransition>()));
        state.getModels().add(model);
        appendAudit("MODEL_REGISTERED", "EngineModel", id, "model registered", changes(
                change("model.id", null, id),
                change("model.name", null, model.getName()),
                change("model.initialState", null, initialState),
                change("model.ontologyTypeId", null, ontologyTypeId),
                change("model.schemaVersion", null, 1),
                change("model.workflowVersion", null, 1)));
        touch(state);
        save();
        return copy(model, EngineModel.class);
    }

    public synchronized EngineModel model(String modelId) {
        return copy(modelRef(modelId), EngineModel.class);
    }

    public synchronized EngineModel updateModelOntologyBinding(String modelId, Map<String, Object> payload) {
        EngineModel model = modelRef(modelId);
        if (payload == null || !payload.containsKey("ontologyTypeId")) {
            throw new IllegalArgumentException("ontologyTypeId is required; use null to unbind");
        }
        String next = optionalOntologyTypeId(payload, "ontologyTypeId");
        String previous = model.getOntologyTypeId();
        if (equalsNullable(previous, next)) {
            return copy(model, EngineModel.class);
        }
        model.setOntologyTypeId(next);
        appendAudit("MODEL_ONTOLOGY_BINDING_UPDATED", "EngineModel", modelId,
                "ontologyTypeId=" + (next == null ? "null" : next), changes(
                        change("model.ontologyTypeId", previous, next)));
        touch(model);
        save();
        return copy(model, EngineModel.class);
    }

    private EngineModel modelRef(String modelId) {
        for (EngineModel model : state.getModels()) {
            if (model.getId().equals(modelId)) {
                return model;
            }
        }
        throw new IllegalArgumentException("model not found: " + modelId);
    }

    public synchronized EngineField addField(String modelId, Map<String, Object> payload) {
        EngineModel model = modelRef(modelId);
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
        int previousVersion = model.getSchemaVersion();
        int version = previousVersion + 1;
        EngineField field = new EngineField(name, type, booleanValue(payload.get("required"), false),
                version, payload.get("defaultValue"));
        validateDefaultValue(field);
        model.getFields().add(field);
        model.setSchemaVersion(version);
        model.getSchemaVersions().add(new SchemaVersionRecord(version, Instant.now().toString(), copyFields(model.getFields())));
        appendAudit("SCHEMA_PUBLISHED", "SchemaVersion", modelId + ":v" + version, "field added: " + name,
                changes(change("schema.version", previousVersion, version),
                        change(fieldPath(name), null, fieldSnapshot(field))));
        touch(model);
        save();
        return copy(field, EngineField.class);
    }

    public synchronized EngineField renameField(String modelId, Map<String, Object> payload) {
        EngineModel model = modelRef(modelId);
        String sourceName = requiredText(payload, "sourceName");
        String targetName = requiredText(payload, "targetName");
        if (sourceName.equals(targetName)) {
            throw new IllegalArgumentException("sourceName and targetName must differ");
        }
        EngineField source = null;
        int sourceIndex = -1;
        for (int index = 0; index < model.getFields().size(); index++) {
            EngineField field = model.getFields().get(index);
            if (sourceName.equals(field.getName())) {
                source = field;
                sourceIndex = index;
            }
            if (targetName.equals(field.getName())) {
                throw new IllegalArgumentException("field already exists: " + targetName);
            }
        }
        if (source == null) {
            throw new IllegalArgumentException("field not found: " + sourceName);
        }
        int fromVersion = model.getSchemaVersion();
        int toVersion = fromVersion + 1;
        EngineField renamed = new EngineField(targetName, source.getType(), source.isRequired(), toVersion,
                source.getDefaultValue());
        Map<String, Object> sourceSnapshot = fieldSnapshot(source);
        model.getFields().remove(sourceIndex);
        model.getFields().add(sourceIndex, renamed);
        model.setSchemaVersion(toVersion);
        model.getSchemaVersions().add(new SchemaVersionRecord(toVersion, Instant.now().toString(), copyFields(model.getFields())));
        model.getSchemaMigrations().add(new SchemaMigrationRecord(fromVersion, toVersion, sourceName, targetName));
        appendAudit("SCHEMA_FIELD_RENAMED", "SchemaVersion", modelId + ":v" + toVersion,
                "field renamed: " + sourceName + " -> " + targetName,
                changes(change("schema.version", fromVersion, toVersion),
                        change(fieldPath(sourceName), sourceSnapshot, null),
                        change(fieldPath(targetName), null, fieldSnapshot(renamed))));
        touch(model);
        save();
        return copy(renamed, EngineField.class);
    }

    public synchronized EngineModel removeField(String modelId, Map<String, Object> payload) {
        EngineModel model = modelRef(modelId);
        String name = requiredText(payload, "name");
        EngineField removed = null;
        for (EngineField field : model.getFields()) {
            if (name.equals(field.getName())) {
                removed = field;
                break;
            }
        }
        if (removed == null) {
            throw new IllegalArgumentException("field not found: " + name);
        }
        int previousVersion = model.getSchemaVersion();
        int version = previousVersion + 1;
        Map<String, Object> removedSnapshot = fieldSnapshot(removed);
        model.getFields().remove(removed);
        model.setSchemaVersion(version);
        model.getSchemaVersions().add(new SchemaVersionRecord(version, Instant.now().toString(), copyFields(model.getFields())));
        appendAudit("SCHEMA_FIELD_REMOVED", "SchemaVersion", modelId + ":v" + version,
                "field removed: " + name,
                changes(change("schema.version", previousVersion, version),
                        change(fieldPath(name), removedSnapshot, null)));
        touch(model);
        save();
        return copy(model, EngineModel.class);
    }

    public synchronized EngineTransition addTransition(String modelId, Map<String, Object> payload) {
        EngineModel model = modelRef(modelId);
        String fromState = requiredText(payload, "fromState");
        String event = requiredText(payload, "event");
        String toState = requiredText(payload, "toState");
        for (EngineTransition transition : model.getTransitions()) {
            if (fromState.equals(transition.getFromState()) && event.equals(transition.getEvent())) {
                throw new IllegalArgumentException("transition already exists: " + fromState + " / " + event);
            }
        }
        List<String> previousStates = new ArrayList<String>(model.getStates());
        if (!model.getStates().contains(fromState)) {
            model.getStates().add(fromState);
        }
        if (!model.getStates().contains(toState)) {
            model.getStates().add(toState);
        }
        int previousVersion = model.getWorkflowVersion();
        EngineTransition transition = new EngineTransition(fromState, event, toState);
        model.getTransitions().add(transition);
        model.setWorkflowVersion(model.getWorkflowVersion() < 1 ? 1 : model.getWorkflowVersion() + 1);
        model.getWorkflowVersions().add(new WorkflowVersionRecord(model.getWorkflowVersion(), Instant.now().toString(),
                model.getInitialState(), copyTransitions(model.getTransitions())));
        List<AuditChangeRecord> workflowChanges = new ArrayList<AuditChangeRecord>();
        workflowChanges.add(change("workflow.version", previousVersion, model.getWorkflowVersion()));
        if (!previousStates.equals(model.getStates())) {
            workflowChanges.add(change("workflow.states", previousStates, model.getStates()));
        }
        workflowChanges.add(change(transitionPath(fromState, event), null, transitionSnapshot(transition)));
        appendAudit("WORKFLOW_PUBLISHED", "WorkflowVersion", modelId + ":v" + model.getWorkflowVersion(),
                "transition added: " + event, workflowChanges);
        touch(model);
        save();
        return copy(transition, EngineTransition.class);
    }

    public synchronized List<OntologyTypeConfig> ontologyTypes() {
        List<OntologyTypeConfig> result = new ArrayList<OntologyTypeConfig>();
        for (OntologyTypeConfig type : state.getOntologyTypes()) {
            result.add(copy(type, OntologyTypeConfig.class));
        }
        return result;
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
        appendAudit("ONTOLOGY_TYPE_REGISTERED", "OntologyType", id, "ontology type registered", changes(
                change("ontology[" + id + "].label", null, type.getLabel()),
                change("ontology[" + id + "].description", null, type.getDescription()),
                change("ontology[" + id + "].fixedAttributes", null, type.getFixedAttributes()),
                change("ontology[" + id + "].dynamicAttributes", null, type.getDynamicAttributes())));
        touch(state);
        save();
        return copy(type, OntologyTypeConfig.class);
    }

    public synchronized OntologyRelationConfig addOntologyRelation(String typeId, Map<String, Object> payload) {
        OntologyTypeConfig type = ontologyTypeRef(typeId);
        String name = requiredText(payload, "name");
        String targetType = requiredText(payload, "targetType");
        String cardinality = OntologyCardinality.parse(requiredText(payload, "cardinality")).getExpression();
        ontologyTypeRef(targetType);
        for (OntologyRelationConfig relation : type.getRelations()) {
            if (name.equals(relation.getName())) {
                throw new IllegalArgumentException("ontology relation already exists: " + name);
            }
        }
        OntologyRelationConfig relation = new OntologyRelationConfig(name, targetType, cardinality);
        type.getRelations().add(relation);
        type.setVersion(type.getVersion() + 1);
        appendAudit("ONTOLOGY_RELATION_REGISTERED", "OntologyRelation", typeId + ":" + name,
                "target=" + targetType + ", cardinality=" + cardinality, changes(
                        change(relationPath(typeId, name) + ".targetType", null, targetType),
                        change(relationPath(typeId, name) + ".cardinality", null, cardinality)));
        touch(state);
        save();
        return copy(relation, OntologyRelationConfig.class);
    }

    public synchronized OntologyRelationConfig updateOntologyRelation(String typeId, String relationName,
                                                                       Map<String, Object> payload) {
        OntologyTypeConfig type = ontologyTypeRef(typeId);
        OntologyRelationConfig relation = null;
        for (OntologyRelationConfig candidate : type.getRelations()) {
            if (relationName.equals(candidate.getName())) {
                relation = candidate;
                break;
            }
        }
        if (relation == null) {
            throw new IllegalArgumentException("ontology relation not found: " + typeId + ":" + relationName);
        }
        String targetType = optionalText(payload, "targetType", relation.getTargetType());
        String cardinality = OntologyCardinality.parse(optionalText(payload, "cardinality", relation.getCardinality()))
                .getExpression();
        ontologyTypeRef(targetType);
        List<AuditChangeRecord> auditChanges = new ArrayList<AuditChangeRecord>();
        if (!targetType.equals(relation.getTargetType())) {
            auditChanges.add(change(relationPath(typeId, relationName) + ".targetType",
                    relation.getTargetType(), targetType));
        }
        if (!cardinality.equals(relation.getCardinality())) {
            auditChanges.add(change(relationPath(typeId, relationName) + ".cardinality",
                    relation.getCardinality(), cardinality));
        }
        if (auditChanges.isEmpty()) {
            return copy(relation, OntologyRelationConfig.class);
        }
        relation.setTargetType(targetType);
        relation.setCardinality(cardinality);
        type.setVersion(type.getVersion() + 1);
        appendAudit("ONTOLOGY_RELATION_UPDATED", "OntologyRelation", type.getId() + ":" + relationName,
                "target=" + targetType + ", cardinality=" + cardinality, auditChanges);
        touch(state);
        save();
        return copy(relation, OntologyRelationConfig.class);
    }

    public synchronized List<ServiceRegistration> services() {
        List<ServiceRegistration> result = new ArrayList<ServiceRegistration>();
        for (ServiceRegistration service : state.getServices()) {
            result.add(copy(service, ServiceRegistration.class));
        }
        return result;
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
        appendAudit("SERVICE_REGISTERED", "Service", id, "endpoint=" + service.getEndpoint(), changes(
                change("service[" + id + "].name", null, service.getName()),
                change("service[" + id + "].provider", null, service.getProvider()),
                change("service[" + id + "].status", null, service.getStatus()),
                change("service[" + id + "].endpoint", null, service.getEndpoint()),
                change("service[" + id + "].version", null, service.getVersion())));
        touch(state);
        save();
        return copy(service, ServiceRegistration.class);
    }

    public synchronized ServiceRegistration updateService(String serviceId, Map<String, Object> payload) {
        ServiceRegistration service = serviceRef(serviceId);
        String name = optionalText(payload, "name", service.getName());
        String provider = optionalText(payload, "provider", service.getProvider());
        String status = optionalText(payload, "status", service.getStatus());
        String endpoint = optionalText(payload, "endpoint", service.getEndpoint());
        String version = optionalText(payload, "version", service.getVersion());
        List<AuditChangeRecord> auditChanges = new ArrayList<AuditChangeRecord>();
        if (!name.equals(service.getName())) {
            auditChanges.add(change("service[" + serviceId + "].name", service.getName(), name));
        }
        if (!provider.equals(service.getProvider())) {
            auditChanges.add(change("service[" + serviceId + "].provider", service.getProvider(), provider));
        }
        if (!status.equals(service.getStatus())) {
            auditChanges.add(change("service[" + serviceId + "].status", service.getStatus(), status));
        }
        if (!endpoint.equals(service.getEndpoint())) {
            auditChanges.add(change("service[" + serviceId + "].endpoint", service.getEndpoint(), endpoint));
        }
        if (!version.equals(service.getVersion())) {
            auditChanges.add(change("service[" + serviceId + "].version", service.getVersion(), version));
        }
        if (auditChanges.isEmpty()) {
            return copy(service, ServiceRegistration.class);
        }
        service.setName(name);
        service.setProvider(provider);
        service.setStatus(status);
        service.setEndpoint(endpoint);
        service.setVersion(version);
        appendAudit("SERVICE_UPDATED", "Service", serviceId,
                "provider=" + service.getProvider() + ", status=" + service.getStatus(), auditChanges);
        touch(state);
        save();
        return copy(service, ServiceRegistration.class);
    }

    public synchronized List<RuntimeRun> runs() {
        List<RuntimeRun> result = new ArrayList<RuntimeRun>();
        for (RuntimeRun run : state.getRuns()) {
            result.add(copy(run, RuntimeRun.class));
        }
        return result;
    }

    /**
     * Returns comparison sessions derived from the persisted Run journal.
     * There is deliberately no second comparison fact source: a session is
     * valid only when its two Run records agree on their reciprocal links,
     * model, event and input identity.
     */
    public synchronized List<ComparisonSummary> comparisons() {
        Map<String, List<RuntimeRun>> grouped = groupedComparisons();
        List<ComparisonSummary> result = new ArrayList<ComparisonSummary>();
        for (List<RuntimeRun> group : grouped.values()) {
            result.add(comparisonSummary(group, false));
        }
        return result;
    }

    public synchronized ComparisonSummary comparison(String comparisonId) {
        if (isBlank(comparisonId)) {
            throw new IllegalArgumentException("comparisonId must not be blank");
        }
        List<RuntimeRun> group = groupedComparisons().get(comparisonId);
        if (group == null) {
            throw new IllegalArgumentException("comparison not found: " + comparisonId);
        }
        return comparisonSummary(group, true);
    }

    private Map<String, List<RuntimeRun>> groupedComparisons() {
        Map<String, List<RuntimeRun>> grouped = new LinkedHashMap<String, List<RuntimeRun>>();
        for (RuntimeRun run : state.getRuns()) {
            if (run == null || isBlank(run.getComparisonId())) {
                continue;
            }
            List<RuntimeRun> group = grouped.get(run.getComparisonId());
            if (group == null) {
                group = new ArrayList<RuntimeRun>();
                grouped.put(run.getComparisonId(), group);
            }
            group.add(run);
        }
        return grouped;
    }

    private ComparisonSummary comparisonSummary(List<RuntimeRun> group, boolean includeRuns) {
        List<RuntimeRun> baselines = new ArrayList<RuntimeRun>();
        List<RuntimeRun> flexibleRuns = new ArrayList<RuntimeRun>();
        for (RuntimeRun run : group) {
            if ("RIGID_MAPPING_BASELINE".equals(run.getExecutionMode())) {
                baselines.add(run);
            } else if ("FLEXIBLE_ENGINE".equals(run.getExecutionMode())) {
                flexibleRuns.add(run);
            }
        }

        RuntimeRun baseline = baselines.size() == 1 ? baselines.get(0) : null;
        RuntimeRun flexible = flexibleRuns.size() == 1 ? flexibleRuns.get(0) : null;
        List<String> issues = new ArrayList<String>();
        if (baselines.size() != 1) {
            issues.add("expected exactly one RIGID_MAPPING_BASELINE run, got " + baselines.size());
        }
        if (flexibleRuns.size() != 1) {
            issues.add("expected exactly one FLEXIBLE_ENGINE run, got " + flexibleRuns.size());
        }
        if (group.size() != 2) {
            issues.add("expected exactly two runs in a comparison session, got " + group.size());
        }

        boolean comparable = baseline != null && flexible != null
                && equalsNullable(baseline.getModelId(), flexible.getModelId())
                && equalsNullable(baseline.getEvent(), flexible.getEvent())
                && !isBlank(baseline.getInputSha256())
                && baseline.getInputSha256().equals(flexible.getInputSha256());
        if (baseline != null && flexible != null && !comparable) {
            issues.add("model, event or input hash is not identical");
        }

        boolean reciprocal = baseline != null && flexible != null
                && equalsNullable(baseline.getPairedRunId(), flexible.getId())
                && equalsNullable(flexible.getPairedRunId(), baseline.getId())
                && equalsNullable(baseline.getComparisonId(), flexible.getComparisonId());
        if (baseline != null && flexible != null && !reciprocal) {
            issues.add("pairedRunId links are not reciprocal");
        }
        boolean configurationDistinct = baseline != null && flexible != null
                && !isBlank(baseline.getConfigurationSha256())
                && !isBlank(flexible.getConfigurationSha256())
                && !baseline.getConfigurationSha256().equals(flexible.getConfigurationSha256());
        if (baseline != null && flexible != null && !configurationDistinct) {
            issues.add("configuration hashes are missing or identical");
        }

        boolean formalPair = baseline != null && flexible != null && comparable && reciprocal
                && configurationDistinct && group.size() == 2;

        boolean evidenceComplete = hasCompleteEvidence(baseline) && hasCompleteEvidence(flexible);
        if (baseline != null && flexible != null && !evidenceComplete) {
            issues.add("one or both runs do not have sealed Trace and before/after Snapshots");
        }

        ComparisonSummary summary = new ComparisonSummary();
        summary.setComparisonId(group.get(0).getComparisonId());
        summary.setCaseId(firstNonBlank(baseline == null ? null : baseline.getCaseId(),
                flexible == null ? null : flexible.getCaseId()));
        summary.setModelId(firstNonBlank(baseline == null ? null : baseline.getModelId(),
                flexible == null ? null : flexible.getModelId()));
        summary.setEvent(firstNonBlank(baseline == null ? null : baseline.getEvent(),
                flexible == null ? null : flexible.getEvent()));
        summary.setCreatedAt(earliestCreatedAt(group));
        summary.setStatus(formalPair ? (evidenceComplete ? "COMPLETE" : "INCOMPLETE")
                : (baseline == null || flexible == null ? "INCOMPLETE" : "INVALID"));
        summary.setOutcome(outcome(baseline, flexible, formalPair));
        summary.setFormalPair(formalPair);
        summary.setComparable(comparable);
        summary.setConfigurationDistinct(configurationDistinct);
        summary.setEvidenceComplete(evidenceComplete);
        summary.setRunCount(group.size());
        summary.setBaselineRunId(baseline == null ? null : baseline.getId());
        summary.setFlexibleRunId(flexible == null ? null : flexible.getId());
        summary.setBaselineStatus(baseline == null ? null : baseline.getStatus());
        summary.setFlexibleStatus(flexible == null ? null : flexible.getStatus());
        summary.setInputSha256(baseline != null ? baseline.getInputSha256() : flexible == null ? null : flexible.getInputSha256());
        summary.setDurationDeltaNs(baseline != null && flexible != null
                ? flexible.getDurationNs() - baseline.getDurationNs() : 0L);
        summary.setIssues(issues);
        if (includeRuns) {
            summary.setBaselineRun(copy(baseline, RuntimeRun.class));
            summary.setFlexibleRun(copy(flexible, RuntimeRun.class));
        }
        return summary;
    }

    private static String outcome(RuntimeRun baseline, RuntimeRun flexible, boolean formalPair) {
        if (!formalPair || baseline == null || flexible == null) {
            return "NOT_AVAILABLE";
        }
        if ("FAILED".equals(baseline.getStatus()) && "PASSED".equals(flexible.getStatus())) {
            return "IMPROVED";
        }
        if ("PASSED".equals(baseline.getStatus()) && "FAILED".equals(flexible.getStatus())) {
            return "REGRESSED";
        }
        return equalsNullable(baseline.getStatus(), flexible.getStatus()) ? "UNCHANGED" : "DIFFERENT";
    }

    private static boolean hasCompleteEvidence(RuntimeRun run) {
        return run != null && run.getBeforeSnapshot() != null && run.getAfterSnapshot() != null
                && run.getTrace() != null && run.getTrace().isSealed()
                && "COMMITTED".equals(run.getTrace().getLifecycle());
    }

    private static String earliestCreatedAt(List<RuntimeRun> group) {
        String earliest = null;
        for (RuntimeRun run : group) {
            if (run.getCreatedAt() != null && (earliest == null || run.getCreatedAt().compareTo(earliest) < 0)) {
                earliest = run.getCreatedAt();
            }
        }
        return earliest;
    }

    private static String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : second;
    }

    public synchronized RuntimeRun execute(Map<String, Object> payload) {
        return copy(runtimeService.execute(payload), RuntimeRun.class);
    }

    public synchronized Map<String, Object> executeComparison(Map<String, Object> payload) {
        Map<String, Object> result = runtimeService.executeComparison(payload);
        Map<String, Object> copied = new LinkedHashMap<String, Object>(result);
        Object baseline = result.get("baselineRun");
        Object flexible = result.get("flexibleRun");
        if (baseline instanceof RuntimeRun) {
            copied.put("baselineRun", copy((RuntimeRun) baseline, RuntimeRun.class));
        }
        if (flexible instanceof RuntimeRun) {
            copied.put("flexibleRun", copy((RuntimeRun) flexible, RuntimeRun.class));
        }
        return copied;
    }

    public synchronized RuntimeRun retry(String runId) {
        return copy(runtimeService.retry(runId), RuntimeRun.class);
    }

    public synchronized RuntimeRun replay(String runId) {
        return copy(runtimeService.replay(runId), RuntimeRun.class);
    }

    public synchronized RuntimeRun rollback(String runId) {
        return copy(runtimeService.rollback(runId), RuntimeRun.class);
    }

    private OntologyTypeConfig ontologyTypeRef(String typeId) {
        for (OntologyTypeConfig type : state.getOntologyTypes()) {
            if (type.getId().equals(typeId) || (type.getLabel() != null && type.getLabel().equalsIgnoreCase(typeId))) {
                return type;
            }
        }
        throw new IllegalArgumentException("ontology type not found: " + typeId);
    }

    private OntologyTypeConfig ontologyTypeRefExact(String typeId) {
        for (OntologyTypeConfig type : state.getOntologyTypes()) {
            if (type.getId().equals(typeId)) {
                return type;
            }
        }
        throw new IllegalArgumentException("ontology type id not found: " + typeId);
    }

    private ServiceRegistration serviceRef(String serviceId) {
        for (ServiceRegistration service : state.getServices()) {
            if (serviceId.equals(service.getId())) {
                return service;
            }
        }
        throw new IllegalArgumentException("service not found: " + serviceId);
    }

    public synchronized RuntimeContextRecord context(String contextId) {
        return copy(contextRef(contextId), RuntimeContextRecord.class);
    }

    public synchronized List<RuntimeContextRecord> contexts() {
        List<RuntimeContextRecord> result = new ArrayList<RuntimeContextRecord>();
        for (RuntimeContextRecord context : state.getContexts()) {
            result.add(copy(context, RuntimeContextRecord.class));
        }
        return result;
    }

    public synchronized RuntimeRun run(String runId) {
        return copy(runRef(runId), RuntimeRun.class);
    }

    private RuntimeContextRecord contextRef(String contextId) {
        for (RuntimeContextRecord context : state.getContexts()) {
            if (contextId.equals(context.getContextId())) {
                return context;
            }
        }
        throw new IllegalArgumentException("context not found: " + contextId);
    }

    private RuntimeRun runRef(String runId) {
        for (RuntimeRun run : state.getRuns()) {
            if (runId.equals(run.getId())) {
                return run;
            }
        }
        throw new IllegalArgumentException("run not found: " + runId);
    }

    public synchronized List<ExecutionSnapshotRecord> snapshots(String runId) {
        RuntimeRun run = runRef(runId);
        List<ExecutionSnapshotRecord> result = new ArrayList<ExecutionSnapshotRecord>();
        if (run.getBeforeSnapshot() != null) {
            result.add(copy(run.getBeforeSnapshot(), ExecutionSnapshotRecord.class));
        }
        if (run.getAfterSnapshot() != null) {
            result.add(copy(run.getAfterSnapshot(), ExecutionSnapshotRecord.class));
        }
        return result;
    }

    public synchronized List<IdempotencyRecord> idempotencyRecords() {
        List<IdempotencyRecord> result = new ArrayList<IdempotencyRecord>();
        for (IdempotencyRecord record : state.getIdempotencyRecords()) {
            result.add(copy(record, IdempotencyRecord.class));
        }
        return result;
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
        List<AuditEventRecord> result = new ArrayList<AuditEventRecord>();
        for (AuditEventRecord event : state.getAuditEvents()) {
            result.add(copy(event, AuditEventRecord.class));
        }
        return result;
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

    private void validateModelContracts() {
        for (EngineModel model : state.getModels()) {
            if (model.getId() == null || model.getId().trim().isEmpty()) {
                throw new IllegalStateException("model id must not be blank");
            }
            if (model.getOntologyTypeId() != null) {
                ontologyTypeRefExact(model.getOntologyTypeId());
            }
            try {
                UnknownFieldPolicy.valueOf(model.getUnknownFieldPolicy().trim().toUpperCase());
            } catch (RuntimeException exception) {
                throw new IllegalStateException("model has an invalid unknownFieldPolicy: " + model.getId(),
                        exception);
            }
            if (model.getSchemaVersion() < 1 || model.getSchemaVersions().isEmpty()) {
                throw new IllegalStateException("model has no valid schema history: " + model.getId());
            }
            Map<Integer, SchemaVersionRecord> schemas = new LinkedHashMap<Integer, SchemaVersionRecord>();
            for (SchemaVersionRecord schema : model.getSchemaVersions()) {
                if (schema.getVersion() < 1 || schemas.put(schema.getVersion(), schema) != null) {
                    throw new IllegalStateException("duplicate schema version in model: " + model.getId());
                }
                Set<String> fieldNames = new HashSet<String>();
                for (EngineField field : schema.getFields()) {
                    if (!fieldNames.add(field.getName())) {
                        throw new IllegalStateException("duplicate field " + field.getName()
                                + " in schema " + model.getId() + ":v" + schema.getVersion());
                    }
                    if (field.getVersion() > schema.getVersion()) {
                        throw new IllegalStateException("field version is newer than its schema: "
                                + model.getId() + ":" + field.getName());
                    }
                    validateDefaultValue(field);
                }
            }
            for (int version = 1; version <= model.getSchemaVersion(); version++) {
                if (!schemas.containsKey(version)) {
                    throw new IllegalStateException("missing schema version " + version + " in model: " + model.getId());
                }
            }
            for (SchemaMigrationRecord migration : model.getSchemaMigrations()) {
                if (migration.getFromVersion() < 1 || migration.getToVersion() != migration.getFromVersion() + 1
                        || !schemas.containsKey(migration.getFromVersion())
                        || !schemas.containsKey(migration.getToVersion())) {
                    throw new IllegalStateException("non-adjacent schema migration in model: " + model.getId());
                }
                if (!containsField(schemas.get(migration.getFromVersion()), migration.getSourceField())
                        || !containsField(schemas.get(migration.getToVersion()), migration.getTargetField())) {
                    throw new IllegalStateException("schema migration references an unknown field in model: "
                            + model.getId());
                }
            }
            if (model.getWorkflowVersion() < 1 || model.getWorkflowVersions().isEmpty()) {
                throw new IllegalStateException("model has no valid workflow history: " + model.getId());
            }
            Map<Integer, WorkflowVersionRecord> workflows = new LinkedHashMap<Integer, WorkflowVersionRecord>();
            for (WorkflowVersionRecord workflow : model.getWorkflowVersions()) {
                if (workflow.getVersion() < 1 || workflows.put(workflow.getVersion(), workflow) != null) {
                    throw new IllegalStateException("duplicate workflow version in model: " + model.getId());
                }
                Set<String> transitions = new HashSet<String>();
                for (EngineTransition transition : workflow.getTransitions()) {
                    String key = transition.getFromState() + "\u0000" + transition.getEvent();
                    if (!transitions.add(key)) {
                        throw new IllegalStateException("duplicate workflow transition in model: " + model.getId());
                    }
                }
            }
            for (int version = 1; version <= model.getWorkflowVersion(); version++) {
                if (!workflows.containsKey(version)) {
                    throw new IllegalStateException("missing workflow version " + version + " in model: " + model.getId());
                }
            }
        }
    }

    private void validateAuditContracts() {
        for (AuditEventRecord event : state.getAuditEvents()) {
            if (event == null || isBlank(event.getId()) || isBlank(event.getAction())
                    || isBlank(event.getTargetType()) || isBlank(event.getTargetId())) {
                throw new IllegalStateException("audit event identity must not be blank");
            }
            if (event.getBeforeRevision() < 0 || event.getAfterRevision() < event.getBeforeRevision()
                    || event.getAfterRevision() > state.getRevision()
                    || (event.getAfterRevision() != 0 && event.getAfterRevision() == event.getBeforeRevision())) {
                throw new IllegalStateException("audit event revision is outside the engine revision chain: "
                        + event.getId());
            }
            for (AuditChangeRecord change : event.getChanges()) {
                if (change == null || isBlank(change.getPath())) {
                    throw new IllegalStateException("audit change path must not be blank: " + event.getId());
                }
            }
        }
    }

    private static boolean containsField(SchemaVersionRecord schema, String name) {
        for (EngineField field : schema.getFields()) {
            if (name.equals(field.getName())) {
                return true;
            }
        }
        return false;
    }

    private static void validateDefaultValue(EngineField field) {
        if (field.getDefaultValue() == null) {
            return;
        }
        FieldDefinition definition = new FieldDefinition(field.getName(), FieldType.valueOf(field.getType()),
                field.isRequired(), field.getVersion(), field.getDefaultValue());
        if (definition.validate(field.getDefaultValue()).isPresent()) {
            throw new IllegalArgumentException("default value does not match field type: " + field.getName());
        }
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
            boolean typeChanged = false;
            if ("questionnaire".equals(type.getId()) && !type.getDynamicAttributes().contains("subjects")) {
                type.getDynamicAttributes().add(0, "subjects");
                changed = true;
                typeChanged = true;
            }
            if ("subject".equals(type.getId()) && !type.getDynamicAttributes().contains("optionCount")) {
                type.getDynamicAttributes().add("optionCount");
                changed = true;
                typeChanged = true;
            }
            if ("option".equals(type.getId()) && !type.getFixedAttributes().contains("label")) {
                type.getFixedAttributes().add("label");
                changed = true;
                typeChanged = true;
            }
            if (typeChanged) {
                type.setVersion(type.getVersion() + 1);
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
        appendAudit(action, targetType, targetId, details, new ArrayList<AuditChangeRecord>());
    }

    private void appendAudit(String action, String targetType, String targetId, String details,
                             List<AuditChangeRecord> changes) {
        state.getAuditEvents().add(0, new AuditEventRecord(
                "audit-" + UUID.randomUUID().toString().substring(0, 8), action, targetType, targetId,
                Instant.now().toString(), details, state.getRevision(), state.getRevision() + 1L,
                changes));
        while (state.getAuditEvents().size() > 200) {
            state.getAuditEvents().remove(state.getAuditEvents().size() - 1);
        }
    }

    private AuditChangeRecord change(String path, Object beforeValue, Object afterValue) {
        return new AuditChangeRecord(path, copyValue(beforeValue), copyValue(afterValue));
    }

    private List<AuditChangeRecord> changes(AuditChangeRecord... changes) {
        List<AuditChangeRecord> result = new ArrayList<AuditChangeRecord>();
        if (changes != null) {
            for (AuditChangeRecord change : changes) {
                if (change != null) {
                    result.add(change);
                }
            }
        }
        return result;
    }

    private Object copyValue(Object value) {
        return value == null ? null : mapper.convertValue(value, Object.class);
    }

    private static Map<String, Object> fieldSnapshot(EngineField field) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("type", field.getType());
        result.put("required", field.isRequired());
        result.put("version", field.getVersion());
        result.put("defaultValue", field.getDefaultValue());
        return result;
    }

    private static Map<String, Object> transitionSnapshot(EngineTransition transition) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("fromState", transition.getFromState());
        result.put("event", transition.getEvent());
        result.put("toState", transition.getToState());
        return result;
    }

    private static String fieldPath(String name) {
        return "schema.fields[" + name + "]";
    }

    private static String transitionPath(String fromState, String event) {
        return "workflow.transitions[" + fromState + ":" + event + "]";
    }

    private static String relationPath(String typeId, String relationName) {
        return "ontology[" + typeId + "].relations[" + relationName + "]";
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
        List<RuntimeRun> result = new ArrayList<RuntimeRun>();
        for (RuntimeRun run : state.getRuns().subList(0, Math.min(limit, state.getRuns().size()))) {
            result.add(copy(run, RuntimeRun.class));
        }
        return result;
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

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String textValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String optionalText(Map<String, Object> payload, String key, String fallback) {
        if (payload == null || payload.get(key) == null) {
            return fallback;
        }
        String value = String.valueOf(payload.get(key)).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(key + " must not be empty");
        }
        return value;
    }

    private String optionalOntologyTypeId(Map<String, Object> payload, String key) {
        if (payload == null || !payload.containsKey(key) || payload.get(key) == null) {
            return null;
        }
        String value = String.valueOf(payload.get(key)).trim();
        if (value.isEmpty()) {
            return null;
        }
        ontologyTypeRefExact(value);
        return value;
    }

    private static boolean equalsNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private <T> T copy(T value, Class<T> type) {
        return mapper.convertValue(value, type);
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
