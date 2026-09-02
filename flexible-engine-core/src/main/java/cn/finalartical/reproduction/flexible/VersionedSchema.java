package cn.finalartical.reproduction.flexible;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VersionedSchema {
    private final Map<Integer, List<FieldDefinition>> versions = new LinkedHashMap<Integer, List<FieldDefinition>>();
    private final List<FieldMigrationRule> migrations = new ArrayList<FieldMigrationRule>();

    public VersionedSchema register(int version, List<FieldDefinition> definitions) {
        if (version < 1 || definitions == null) {
            throw new IllegalArgumentException("schema version and definitions must be valid");
        }
        int expectedVersion = versions.isEmpty() ? 1 : versions.keySet().stream()
                .max(Integer::compareTo).get() + 1;
        if (version != expectedVersion) {
            throw new IllegalArgumentException("schema versions must be published sequentially; expected "
                    + expectedVersion + " but was " + version);
        }
        if (versions.containsKey(version)) {
            throw new IllegalArgumentException("schema version already registered: " + version);
        }
        List<FieldDefinition> copied = new ArrayList<FieldDefinition>();
        java.util.Set<String> names = new java.util.LinkedHashSet<String>();
        for (FieldDefinition definition : definitions) {
            if (definition == null || !names.add(definition.getName())) {
                throw new IllegalArgumentException("schema contains a null or duplicate field");
            }
            copied.add(definition);
        }
        versions.put(version, Collections.unmodifiableList(copied));
        return this;
    }

    public VersionedSchema addMigration(FieldMigrationRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("migration rule must not be null");
        }
        migrations.add(rule);
        return this;
    }

    public List<String> validate(int version, DynamicRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        List<FieldDefinition> definitions = requireVersion(version);
        return record.validate(definitions);
    }

    public DynamicRecord migrate(DynamicRecord source, int fromVersion, int toVersion) {
        if (source == null) {
            throw new IllegalArgumentException("source record must not be null");
        }
        if (fromVersion > toVersion) {
            throw new IllegalArgumentException("schema migration must move forward");
        }
        requireVersion(fromVersion);
        List<FieldDefinition> targetDefinitions = requireVersion(toVersion);
        DynamicRecord migrated = new DynamicRecord();
        for (FieldDefinition target : targetDefinitions) {
            if (source.contains(target.getName())) {
                migrated.put(target.getName(), source.get(target.getName()));
                continue;
            }
            for (FieldMigrationRule rule : migrations) {
                if (rule.getTargetName().equals(target.getName()) && source.contains(rule.getSourceName())) {
                    migrated.put(target.getName(), source.get(rule.getSourceName()));
                    break;
                }
                if (rule.getTargetName().equals(target.getName()) && rule.getDefaultValue() != null) {
                    migrated.put(target.getName(), rule.getDefaultValue());
                    break;
                }
            }
        }
        if (fromVersion == toVersion) {
            for (FieldDefinition target : targetDefinitions) {
                if (!migrated.contains(target.getName()) && source.contains(target.getName())) {
                    migrated.put(target.getName(), source.get(target.getName()));
                }
            }
        }
        return migrated;
    }

    private List<FieldDefinition> requireVersion(int version) {
        List<FieldDefinition> definitions = versions.get(version);
        if (definitions == null) {
            throw new IllegalArgumentException("schema version not registered: " + version);
        }
        return definitions;
    }
}
