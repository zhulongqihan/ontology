package cn.finalartical.reproduction.admin;

/**
 * A single explainable configuration change inside an audit event.
 *
 * <p>The values intentionally remain JSON-compatible objects so the audit
 * record can be exported without coupling the control plane to one domain
 * aggregate type.</p>
 */
public class AuditChangeRecord {
    private String path;
    private Object beforeValue;
    private Object afterValue;

    public AuditChangeRecord() {
    }

    public AuditChangeRecord(String path, Object beforeValue, Object afterValue) {
        this.path = path;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Object getBeforeValue() {
        return beforeValue;
    }

    public void setBeforeValue(Object beforeValue) {
        this.beforeValue = beforeValue;
    }

    public Object getAfterValue() {
        return afterValue;
    }

    public void setAfterValue(Object afterValue) {
        this.afterValue = afterValue;
    }
}
