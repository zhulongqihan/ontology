package cn.finalartical.reproduction.admin;

import java.util.ArrayList;
import java.util.List;

public class AuditEventRecord {
    private String id;
    private String action;
    private String targetType;
    private String targetId;
    private String createdAt;
    private String details;
    private long beforeRevision;
    private long afterRevision;
    private List<AuditChangeRecord> changes = new ArrayList<AuditChangeRecord>();

    public AuditEventRecord() {
    }

    public AuditEventRecord(String id, String action, String targetType, String targetId, String createdAt, String details) {
        this(id, action, targetType, targetId, createdAt, details, 0L, 0L);
    }

    public AuditEventRecord(String id, String action, String targetType, String targetId, String createdAt,
                            String details, long beforeRevision, long afterRevision) {
        this(id, action, targetType, targetId, createdAt, details, beforeRevision, afterRevision,
                new ArrayList<AuditChangeRecord>());
    }

    public AuditEventRecord(String id, String action, String targetType, String targetId, String createdAt,
                            String details, long beforeRevision, long afterRevision,
                            List<AuditChangeRecord> changes) {
        this.id = id;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.createdAt = createdAt;
        this.details = details;
        this.beforeRevision = beforeRevision;
        this.afterRevision = afterRevision;
        setChanges(changes);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public long getBeforeRevision() { return beforeRevision; }
    public void setBeforeRevision(long beforeRevision) { this.beforeRevision = beforeRevision; }
    public long getAfterRevision() { return afterRevision; }
    public void setAfterRevision(long afterRevision) { this.afterRevision = afterRevision; }
    public List<AuditChangeRecord> getChanges() { return changes; }
    public void setChanges(List<AuditChangeRecord> changes) {
        this.changes = changes == null ? new ArrayList<AuditChangeRecord>() : changes;
    }
}
