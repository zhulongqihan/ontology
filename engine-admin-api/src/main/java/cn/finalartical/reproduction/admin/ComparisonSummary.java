package cn.finalartical.reproduction.admin;

import java.util.ArrayList;
import java.util.List;

/**
 * A read-only projection of a persisted baseline/flexible pair.
 *
 * The control plane derives this object from RuntimeRun records instead of
 * maintaining a second comparison state.  The optional run fields are only
 * populated by the detail endpoint; list responses stay small and suitable
 * for a history selector.
 */
public class ComparisonSummary {
    private String comparisonId;
    private String caseId;
    private String modelId;
    private String event;
    private String createdAt;
    private String status;
    private String outcome;
    private boolean formalPair;
    private boolean comparable;
    private boolean configurationDistinct;
    private boolean evidenceComplete;
    private int runCount;
    private String baselineRunId;
    private String flexibleRunId;
    private String baselineStatus;
    private String flexibleStatus;
    private String inputSha256;
    private long durationDeltaNs;
    private List<String> issues = new ArrayList<String>();
    private RuntimeRun baselineRun;
    private RuntimeRun flexibleRun;

    public ComparisonSummary() {
    }

    public String getComparisonId() { return comparisonId; }
    public void setComparisonId(String comparisonId) { this.comparisonId = comparisonId; }
    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public boolean isFormalPair() { return formalPair; }
    public void setFormalPair(boolean formalPair) { this.formalPair = formalPair; }
    public boolean isComparable() { return comparable; }
    public void setComparable(boolean comparable) { this.comparable = comparable; }
    public boolean isConfigurationDistinct() { return configurationDistinct; }
    public void setConfigurationDistinct(boolean configurationDistinct) { this.configurationDistinct = configurationDistinct; }
    public boolean isEvidenceComplete() { return evidenceComplete; }
    public void setEvidenceComplete(boolean evidenceComplete) { this.evidenceComplete = evidenceComplete; }
    public int getRunCount() { return runCount; }
    public void setRunCount(int runCount) { this.runCount = runCount; }
    public String getBaselineRunId() { return baselineRunId; }
    public void setBaselineRunId(String baselineRunId) { this.baselineRunId = baselineRunId; }
    public String getFlexibleRunId() { return flexibleRunId; }
    public void setFlexibleRunId(String flexibleRunId) { this.flexibleRunId = flexibleRunId; }
    public String getBaselineStatus() { return baselineStatus; }
    public void setBaselineStatus(String baselineStatus) { this.baselineStatus = baselineStatus; }
    public String getFlexibleStatus() { return flexibleStatus; }
    public void setFlexibleStatus(String flexibleStatus) { this.flexibleStatus = flexibleStatus; }
    public String getInputSha256() { return inputSha256; }
    public void setInputSha256(String inputSha256) { this.inputSha256 = inputSha256; }
    public long getDurationDeltaNs() { return durationDeltaNs; }
    public void setDurationDeltaNs(long durationDeltaNs) { this.durationDeltaNs = durationDeltaNs; }
    public List<String> getIssues() { return issues; }
    public void setIssues(List<String> issues) {
        this.issues = issues == null ? new ArrayList<String>() : new ArrayList<String>(issues);
    }
    public RuntimeRun getBaselineRun() { return baselineRun; }
    public void setBaselineRun(RuntimeRun baselineRun) { this.baselineRun = baselineRun; }
    public RuntimeRun getFlexibleRun() { return flexibleRun; }
    public void setFlexibleRun(RuntimeRun flexibleRun) { this.flexibleRun = flexibleRun; }
}
