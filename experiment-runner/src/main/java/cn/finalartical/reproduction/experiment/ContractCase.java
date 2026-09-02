package cn.finalartical.reproduction.experiment;

public final class ContractCase {
    private final String caseId;
    private final String capability;
    private final String scenario;
    private final String requestShape;
    private final String expectedBehavior;
    private final String specBasis;

    public ContractCase(String caseId, String capability, String scenario, String requestShape,
                        String expectedBehavior, String specBasis) {
        this.caseId = caseId;
        this.capability = capability;
        this.scenario = scenario;
        this.requestShape = requestShape;
        this.expectedBehavior = expectedBehavior;
        this.specBasis = specBasis;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getCapability() {
        return capability;
    }

    public String getScenario() {
        return scenario;
    }

    public String getRequestShape() {
        return requestShape;
    }

    public String getExpectedBehavior() {
        return expectedBehavior;
    }

    public String getSpecBasis() {
        return specBasis;
    }
}
