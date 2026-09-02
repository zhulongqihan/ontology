package cn.finalartical.reproduction.flexible;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SchemaDefinition {
    private final String modelId;
    private final Map<Integer, SchemaVersion> versions = new LinkedHashMap<Integer, SchemaVersion>();

    public SchemaDefinition(String modelId) {
        if (modelId == null || modelId.trim().isEmpty()) {
            throw new IllegalArgumentException("schema model id must not be blank");
        }
        this.modelId = modelId;
    }

    public synchronized SchemaDefinition publish(SchemaVersion version) {
        if (version == null) {
            throw new IllegalArgumentException("schema version must not be null");
        }
        if (versions.containsKey(version.getVersion())) {
            throw new IllegalArgumentException("schema version already published: " + version.getVersion());
        }
        if (!versions.isEmpty() && version.getVersion() != currentVersion() + 1) {
            throw new IllegalArgumentException("schema versions must be published sequentially");
        }
        versions.put(version.getVersion(), version);
        return this;
    }

    public String getModelId() {
        return modelId;
    }

    public synchronized int currentVersion() {
        return versions.isEmpty() ? 0 : new java.util.TreeMap<Integer, SchemaVersion>(versions).lastKey();
    }

    public synchronized SchemaVersion version(int version) {
        SchemaVersion result = versions.get(version);
        if (result == null) {
            throw new IllegalArgumentException("schema version not found: " + version);
        }
        return result;
    }

    public synchronized Map<Integer, SchemaVersion> versions() {
        return Collections.unmodifiableMap(new LinkedHashMap<Integer, SchemaVersion>(versions));
    }
}
