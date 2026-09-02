package cn.finalartical.reproduction.experiment;

public final class ContractExecution {
    private final String caseId;
    private final String capability;
    private final String scenario;
    private final String requestShape;
    private final String expectedBehavior;
    private final String rawStatus;
    private final String actualBehavior;
    private final String traceId;
    private final boolean passed;
    private final String dataIdentity;
    private final String requestJson;
    private final String responseJson;
    private final String traceJson;

    public ContractExecution(String caseId, String capability, String scenario, String requestShape,
                             String expectedBehavior, String rawStatus, String actualBehavior,
                             String traceId, boolean passed, String dataIdentity,
                             String requestJson, String responseJson, String traceJson) {
        this.caseId = caseId;
        this.capability = capability;
        this.scenario = scenario;
        this.requestShape = requestShape;
        this.expectedBehavior = expectedBehavior;
        this.rawStatus = rawStatus;
        this.actualBehavior = actualBehavior;
        this.traceId = traceId;
        this.passed = passed;
        this.dataIdentity = dataIdentity;
        this.requestJson = requestJson;
        this.responseJson = responseJson;
        this.traceJson = traceJson;
    }

    public boolean isPassed() {
        return passed;
    }

    public String getRawStatus() {
        return rawStatus;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getRequestJson() {
        return requestJson;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public String getTraceJson() {
        return traceJson;
    }

    public String getResultJson() {
        return toJson();
    }

    public String toJson() {
        return "{"
                + "\"case_id\":" + quote(caseId)
                + ",\"capability\":" + quote(capability)
                + ",\"scenario\":" + quote(scenario)
                + ",\"request_shape\":" + quote(requestShape)
                + ",\"expected_behavior\":" + quote(expectedBehavior)
                + ",\"raw_status\":" + quote(rawStatus)
                + ",\"actual_behavior\":" + quote(actualBehavior)
                + ",\"trace_id\":" + quote(traceId)
                + ",\"passed\":" + passed
                + ",\"data_identity\":" + quote(dataIdentity)
                + "}";
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
