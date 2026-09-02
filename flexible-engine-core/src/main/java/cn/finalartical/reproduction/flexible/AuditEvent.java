package cn.finalartical.reproduction.flexible;

import java.time.Instant;

public final class AuditEvent {
    private final String id;
    private final String action;
    private final String targetType;
    private final String targetId;
    private final String createdAt;
    private final String details;

    public AuditEvent(String id, String action, String targetType, String targetId, String details) {
        if (isBlank(id) || isBlank(action) || isBlank(targetType) || isBlank(targetId)) {
            throw new IllegalArgumentException("audit event identity must not be blank");
        }
        this.id = id;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.createdAt = Instant.now().toString();
        this.details = details == null ? "" : details;
    }

    public String getId() { return id; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getCreatedAt() { return createdAt; }
    public String getDetails() { return details; }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
