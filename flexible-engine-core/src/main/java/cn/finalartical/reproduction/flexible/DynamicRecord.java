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
        List<String> errors = new ArrayList<String>();
        for (FieldDefinition definition : definitions) {
            java.util.Optional<String> error = definition.validate(values.get(definition.getName()));
            if (error.isPresent()) {
                errors.add(error.get());
            }
        }
        return Collections.unmodifiableList(errors);
    }
}
