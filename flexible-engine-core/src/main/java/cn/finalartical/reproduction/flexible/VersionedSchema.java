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
        versions.put(version, Collections.unmodifiableList(new ArrayList<FieldDefinition>(definitions)));
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
        List<FieldDefinition> definitions = requireVersion(version);
        return record.validate(definitions);
    }

    public DynamicRecord migrate(DynamicRecord source, int fromVersion, int toVersion) {
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
