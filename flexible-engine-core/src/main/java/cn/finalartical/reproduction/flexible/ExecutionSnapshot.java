package cn.finalartical.reproduction.flexible;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExecutionSnapshot {
    private final String contextId;
    private final String modelId;
    private final int schemaVersion;
    private final int workflowVersion;
    private final String state;
    private final ExecutionStatus status;
    private final String capturedAt;
    private final Map<String, Object> values;
    private final String sha256;

    public ExecutionSnapshot(String contextId, String modelId, int schemaVersion, int workflowVersion,
                             String state, ExecutionStatus status, String capturedAt, Map<String, ?> values) {
        if (isBlank(contextId) || isBlank(modelId) || isBlank(state) || isBlank(capturedAt)) {
            throw new IllegalArgumentException("snapshot identity and state must not be blank");
        }
        if (schemaVersion < 1 || workflowVersion < 1 || status == null || values == null) {
            throw new IllegalArgumentException("snapshot metadata must be valid");
        }
        this.contextId = contextId;
        this.modelId = modelId;
        this.schemaVersion = schemaVersion;
        this.workflowVersion = workflowVersion;
        this.state = state;
        this.status = status;
        this.capturedAt = capturedAt;
        this.values = freezeMap(values);

        Map<String, Object> hashSource = new LinkedHashMap<String, Object>();
        hashSource.put("contextId", contextId);
        hashSource.put("modelId", modelId);
        hashSource.put("schemaVersion", schemaVersion);
        hashSource.put("workflowVersion", workflowVersion);
        hashSource.put("state", state);
        hashSource.put("status", status.name());
        hashSource.put("values", this.values);
        this.sha256 = new ContextSnapshot(hashSource).getSha256();
    }

    public String getContextId() {
        return contextId;
    }

    public String getModelId() {
        return modelId;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public int getWorkflowVersion() {
        return workflowVersion;
    }

    public String getState() {
        return state;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public String getCapturedAt() {
        return capturedAt;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public String getSha256() {
        return sha256;
    }

    private static Map<String, Object> freezeMap(Map<String, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey().trim().isEmpty()) {
                throw new IllegalArgumentException("snapshot value key must not be blank");
            }
            copy.put(entry.getKey(), freeze(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Object freeze(Object value) {
        if (value instanceof Map) {
            Map<String, Object> nested = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                nested.put(String.valueOf(entry.getKey()), freeze(entry.getValue()));
            }
            return Collections.unmodifiableMap(nested);
        }
        if (value instanceof List) {
            List<Object> nested = new ArrayList<Object>();
            for (Object item : (List<?>) value) {
                nested.add(freeze(item));
            }
            return Collections.unmodifiableList(nested);
        }
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
