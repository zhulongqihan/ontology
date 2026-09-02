package cn.finalartical.reproduction.flexible;

import java.util.Objects;

public final class IdempotencyKey {
    private final String value;

    public IdempotencyKey(String value) {
        if (value == null || value.trim().isEmpty() || value.length() > 200) {
            throw new IllegalArgumentException("idempotency key must be 1-200 characters");
        }
        this.value = value.trim();
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof IdempotencyKey && value.equals(((IdempotencyKey) other).value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
