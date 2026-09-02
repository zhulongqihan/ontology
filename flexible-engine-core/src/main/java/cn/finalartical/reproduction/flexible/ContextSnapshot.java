package cn.finalartical.reproduction.flexible;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.util.TreeMap;

public final class ContextSnapshot {
    private final Map<String, String> values;
    private final String sha256;

    public ContextSnapshot(Map<String, ?> source) {
        if (source == null) {
            throw new IllegalArgumentException("snapshot source must not be null");
        }
        Map<String, Object> normalized = new TreeMap<String, Object>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException("snapshot key must not be null");
            }
            normalized.put(entry.getKey(), entry.getValue());
        }
        Map<String, String> displayValues = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Object> entry : normalized.entrySet()) {
            displayValues.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        this.values = Collections.unmodifiableMap(displayValues);
        this.sha256 = digest(canonicalize(normalized));
    }

    public Map<String, String> getValues() {
        return values;
    }

    public String getSha256() {
        return sha256;
    }

    private static String canonicalize(Map<String, ?> values) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            builder.append(quote(entry.getKey())).append(':').append(canonicalizeValue(entry.getValue())).append(';');
        }
        return builder.toString();
    }

    private static String canonicalizeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map) {
            List<Map.Entry<String, Object>> entries = new ArrayList<Map.Entry<String, Object>>();
            for (Object item : ((Map<?, ?>) value).entrySet()) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) item;
                entries.add(new EntryView(String.valueOf(entry.getKey()), entry.getValue()));
            }
            Collections.sort(entries, new Comparator<Map.Entry<String, Object>>() {
                @Override
                public int compare(Map.Entry<String, Object> left, Map.Entry<String, Object> right) {
                    return left.getKey().compareTo(right.getKey());
                }
            });
            StringBuilder builder = new StringBuilder("{");
            for (Map.Entry<String, Object> entry : entries) {
                builder.append(quote(entry.getKey())).append(':').append(canonicalizeValue(entry.getValue())).append(';');
            }
            return builder.append('}').toString();
        }
        if (value instanceof Iterable) {
            StringBuilder builder = new StringBuilder("[");
            for (Object item : (Iterable<?>) value) {
                builder.append(canonicalizeValue(item)).append(',');
            }
            return builder.append(']').toString();
        }
        if (value.getClass().isArray()) {
            StringBuilder builder = new StringBuilder("[");
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                builder.append(canonicalizeValue(Array.get(value, index))).append(',');
            }
            return builder.append(']').toString();
        }
        if (value instanceof Number) {
            return new BigDecimal(String.valueOf(value)).stripTrailingZeros().toPlainString();
        }
        if (value instanceof Boolean) {
            return String.valueOf(value);
        }
        return quote(String.valueOf(value));
    }

    private static String quote(String value) {
        StringBuilder builder = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\' || character == '"') {
                builder.append('\\');
            }
            if (character == '\n') {
                builder.append("\\n");
            } else if (character == '\r') {
                builder.append("\\r");
            } else if (character == '\t') {
                builder.append("\\t");
            } else {
                builder.append(character);
            }
        }
        return builder.append('"').toString();
    }

    private static final class EntryView implements Map.Entry<String, Object> {
        private final String key;
        private Object value;

        private EntryView(String key, Object value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public Object getValue() {
            return value;
        }

        @Override
        public Object setValue(Object value) {
            Object previous = this.value;
            this.value = value;
            return previous;
        }
    }

    private static String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
