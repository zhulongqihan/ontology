package cn.finalartical.reproduction.compatibility;

public final class OperationResult<T> {
    private final OperationStatus status;
    private final String message;
    private final String traceId;
    private final T data;

    private OperationResult(OperationStatus status, String message, String traceId, T data) {
        this.status = status;
        this.message = message;
        this.traceId = traceId;
        this.data = data;
    }

    public static <T> OperationResult<T> of(OperationStatus status, String message, String traceId, T data) {
        return new OperationResult<T>(status, message, traceId, data);
    }

    public OperationStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getTraceId() {
        return traceId;
    }

    public T getData() {
        return data;
    }
}
