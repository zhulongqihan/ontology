package cn.finalartical.reproduction.admin;

public class EngineField {
    private String name;
    private String type;
    private boolean required;
    private int version;
    private Object defaultValue;

    public EngineField() {
    }

    public EngineField(String name, String type, boolean required, int version, Object defaultValue) {
        this.name = name;
        this.type = type;
        this.required = required;
        this.version = version;
        this.defaultValue = defaultValue;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }
}
