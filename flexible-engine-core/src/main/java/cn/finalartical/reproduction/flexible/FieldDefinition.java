package cn.finalartical.reproduction.flexible;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

public final class FieldDefinition {
    private final String name;
    private final FieldType type;
    private final boolean required;
    private final int version;

    public FieldDefinition(String name, FieldType type, boolean required, int version) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("field name must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("field type must not be null");
        }
        if (version < 1) {
            throw new IllegalArgumentException("field version must be positive");
        }
        this.name = name;
        this.type = type;
        this.required = required;
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public FieldType getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    public int getVersion() {
        return version;
    }

    public Optional<String> validate(Object value) {
        if (value == null) {
            return required ? Optional.of(name + " is required") : Optional.<String>empty();
        }
        if (!matchesType(value)) {
            return Optional.of(name + " expects " + type + " but received " + value.getClass().getSimpleName());
        }
        return Optional.empty();
    }

    private boolean matchesType(Object value) {
        switch (type) {
            case STRING:
                return value instanceof String;
            case INTEGER:
                return value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long;
            case DECIMAL:
                return value instanceof BigDecimal || value instanceof Float || value instanceof Double;
            case BOOLEAN:
                return value instanceof Boolean;
            case JSON:
                return value instanceof String || value instanceof java.util.Map || value instanceof java.util.List;
            case OBJECT:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FieldDefinition)) {
            return false;
        }
        FieldDefinition that = (FieldDefinition) other;
        return required == that.required
                && version == that.version
                && name.equals(that.name)
                && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, required, version);
    }

    @Override
    public String toString() {
        return "FieldDefinition{" + name + ", " + type + ", required=" + required + ", version=" + version + '}';
    }
}
