package cn.finalartical.reproduction.flexible;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public final class ContextSnapshot {
    private final Map<String, String> values;
    private final String sha256;

    public ContextSnapshot(Map<String, ?> source) {
        if (source == null) {
            throw new IllegalArgumentException("snapshot source must not be null");
        }
        Map<String, String> normalized = new TreeMap<String, String>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException("snapshot key must not be null");
            }
            normalized.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        this.values = Collections.unmodifiableMap(normalized);
        this.sha256 = digest(canonicalize(normalized));
    }

    public Map<String, String> getValues() {
        return values;
    }

    public String getSha256() {
        return sha256;
    }

    private static String canonicalize(Map<String, String> values) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            builder.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        return builder.toString();
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
