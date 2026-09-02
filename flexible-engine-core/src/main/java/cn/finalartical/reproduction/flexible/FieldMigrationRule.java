package cn.finalartical.reproduction.flexible;

public final class FieldMigrationRule {
    private final String sourceName;
    private final String targetName;
    private final Object defaultValue;

    public FieldMigrationRule(String sourceName, String targetName, Object defaultValue) {
        if (isBlank(sourceName) || isBlank(targetName)) {
            throw new IllegalArgumentException("migration field names must not be blank");
        }
        this.sourceName = sourceName;
        this.targetName = targetName;
        this.defaultValue = defaultValue;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getTargetName() {
        return targetName;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
