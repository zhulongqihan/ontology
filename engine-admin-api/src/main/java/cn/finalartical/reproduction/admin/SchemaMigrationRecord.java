package cn.finalartical.reproduction.admin;

public class SchemaMigrationRecord {
    private int fromVersion;
    private int toVersion;
    private String sourceField;
    private String targetField;

    public SchemaMigrationRecord() {
    }

    public SchemaMigrationRecord(int fromVersion, int toVersion, String sourceField, String targetField) {
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
        this.sourceField = sourceField;
        this.targetField = targetField;
    }

    public int getFromVersion() { return fromVersion; }
    public void setFromVersion(int fromVersion) { this.fromVersion = fromVersion; }
    public int getToVersion() { return toVersion; }
    public void setToVersion(int toVersion) { this.toVersion = toVersion; }
    public String getSourceField() { return sourceField; }
    public void setSourceField(String sourceField) { this.sourceField = sourceField; }
    public String getTargetField() { return targetField; }
    public void setTargetField(String targetField) { this.targetField = targetField; }
}
