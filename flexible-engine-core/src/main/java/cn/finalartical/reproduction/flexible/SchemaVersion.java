package cn.finalartical.reproduction.flexible;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SchemaVersion {
    private final int version;
    private final String publishedAt;
    private final List<FieldDefinition> fields;

    public SchemaVersion(int version, List<FieldDefinition> fields) {
        this(version, fields, Instant.now().toString());
    }

    public SchemaVersion(int version, List<FieldDefinition> fields, String publishedAt) {
        if (version < 1 || fields == null || publishedAt == null || publishedAt.trim().isEmpty()) {
            throw new IllegalArgumentException("schema version values must be valid");
        }
        Map<String, FieldDefinition> unique = new LinkedHashMap<String, FieldDefinition>();
        for (FieldDefinition field : fields) {
            if (field == null || unique.put(field.getName(), field) != null) {
                throw new IllegalArgumentException("schema field names must be unique");
            }
        }
        this.version = version;
        this.publishedAt = publishedAt;
        this.fields = Collections.unmodifiableList(new ArrayList<FieldDefinition>(unique.values()));
    }

    public int getVersion() {
        return version;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public List<FieldDefinition> getFields() {
        return fields;
    }

    public FieldDefinition field(String name) {
        for (FieldDefinition field : fields) {
            if (field.getName().equals(name)) {
                return field;
            }
        }
        return null;
    }
}
