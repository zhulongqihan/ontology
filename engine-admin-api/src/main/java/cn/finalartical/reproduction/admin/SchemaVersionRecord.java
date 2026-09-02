package cn.finalartical.reproduction.admin;

import java.util.ArrayList;
import java.util.List;

public class SchemaVersionRecord {
    private int version;
    private String publishedAt;
    private List<EngineField> fields = new ArrayList<EngineField>();

    public SchemaVersionRecord() {
    }

    public SchemaVersionRecord(int version, String publishedAt, List<EngineField> fields) {
        this.version = version;
        this.publishedAt = publishedAt;
        setFields(fields);
    }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getPublishedAt() { return publishedAt; }
    public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }
    public List<EngineField> getFields() { return fields; }
    public void setFields(List<EngineField> fields) {
        this.fields = fields == null ? new ArrayList<EngineField>() : new ArrayList<EngineField>(fields);
    }
}
