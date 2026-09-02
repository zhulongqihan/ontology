package cn.finalartical.reproduction.flexible;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DynamicRecord {
    private final Map<String, Object> values = new LinkedHashMap<String, Object>();

    public DynamicRecord put(String fieldName, Object value) {
        if (fieldName == null || fieldName.trim().isEmpty()) {
            throw new IllegalArgumentException("field name must not be blank");
        }
        values.put(fieldName, value);
        return this;
    }

    public Object get(String fieldName) {
        return values.get(fieldName);
    }

    public boolean contains(String fieldName) {
        return values.containsKey(fieldName);
    }

    public DynamicRecord copy() {
        DynamicRecord copy = new DynamicRecord();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, Object>(values));
    }

    public List<String> validate(Collection<FieldDefinition> definitions) {
        return validate(definitions, UnknownFieldPolicy.IGNORE);
    }

    public List<String> validate(Collection<FieldDefinition> definitions, UnknownFieldPolicy unknownFieldPolicy) {
        if (definitions == null || unknownFieldPolicy == null) {
            throw new IllegalArgumentException("definitions and unknown field policy must be valid");
        }
        List<String> errors = new ArrayList<String>();
        java.util.Set<String> knownFields = new java.util.LinkedHashSet<String>();
        for (FieldDefinition definition : definitions) {
            if (definition == null) {
                throw new IllegalArgumentException("field definition must not be null");
            }
            knownFields.add(definition.getName());
            java.util.Optional<String> error = definition.validate(values.get(definition.getName()));
            if (error.isPresent()) {
                errors.add(error.get());
            }
        }
        if (unknownFieldPolicy == UnknownFieldPolicy.REJECT) {
            for (String fieldName : values.keySet()) {
                if (!knownFields.contains(fieldName)) {
                    errors.add("unknown field: " + fieldName);
                }
            }
        }
        return Collections.unmodifiableList(errors);
    }
}
