package cn.finalartical.reproduction.experiment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExperimentRunReport {
    private final String experimentId;
    private final long seed;
    private final List<ContractExecution> executions;

    public ExperimentRunReport(String experimentId, long seed, List<ContractExecution> executions) {
        this.experimentId = experimentId;
        this.seed = seed;
        this.executions = Collections.unmodifiableList(new ArrayList<ContractExecution>(executions));
    }

    public int getTotal() {
        return executions.size();
    }

    public int getPassed() {
        int count = 0;
        for (ContractExecution execution : executions) {
            if (execution.isPassed()) {
                count++;
            }
        }
        return count;
    }

    public int getFailed() {
        return getTotal() - getPassed();
    }

    public long getSeed() {
        return seed;
    }

    public String getRunId() {
        return experimentId + "-seed-" + seed;
    }

    public List<ContractExecution> getExecutions() {
        return executions;
    }

    public String toJson() {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"experiment_id\":\"").append(experimentId)
                .append("\",\"run_id\":\"").append(getRunId())
                .append("\",\"seed\":").append(seed)
                .append(",\"data_identity\":\"REPRODUCED_SYSTEM_RUN\"")
                .append(",\"total\":").append(getTotal())
                .append(",\"passed\":").append(getPassed())
                .append(",\"failed\":").append(getFailed())
                .append(",\"executions\":[");
        for (int index = 0; index < executions.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(executions.get(index).toJson());
        }
        return builder.append("]}").toString();
    }
}
