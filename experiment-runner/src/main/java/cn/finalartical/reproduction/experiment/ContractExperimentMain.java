package cn.finalartical.reproduction.experiment;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class ContractExperimentMain {
    private ContractExperimentMain() {
    }

    public static void main(String[] args) throws Exception {
        Path cases = args.length > 0
                ? Paths.get(args[0])
                : Paths.get("experiments", "contract-20", "contract-20.csv");
        Path output = args.length > 1
                ? Paths.get(args[1])
                : Paths.get("runs", "contract-20", "20260902");
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 20260902L;
        ContractExperimentRunner runner = new ContractExperimentRunner();
        ExperimentRunReport report = runner.runFromCsv(cases, seed);
        runner.writeArtifacts(report, output);
        System.out.println("experiment=" + "contract-20");
        System.out.println("data_identity=" + ExperimentRunReport.DATA_IDENTITY);
        System.out.println("total=" + report.getTotal());
        System.out.println("passed=" + report.getPassed());
        System.out.println("failed=" + report.getFailed());
        System.out.println("output=" + output.toAbsolutePath());
    }
}
