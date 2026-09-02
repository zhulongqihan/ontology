package cn.finalartical.reproduction.experiment;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ContractExperimentRunnerTest {
    @Test
    public void executesAllTwentyContractSpecifications() throws Exception {
        Path caseFile = Paths.get("..", "experiments", "contract-20", "contract-20.csv").toAbsolutePath().normalize();
        List<ContractCase> cases = new ContractCsvLoader().load(caseFile);
        ExperimentRunReport report = new ContractExperimentRunner().run(cases, 20260902L);

        assertEquals(20, report.getTotal());
        assertEquals(20, report.getPassed());
        assertEquals(0, report.getFailed());
        assertTrue(report.toJson().contains("REPRODUCED_SYSTEM_RUN"));
    }

    @Test
    public void writesMachineReadableReport() throws Exception {
        Path caseFile = Paths.get("..", "experiments", "contract-20", "contract-20.csv").toAbsolutePath().normalize();
        ExperimentRunReport report = new ContractExperimentRunner().runFromCsv(caseFile, 20260902L);
        Path output = Files.createTempFile("contract-20-", ".json");
        try {
            new ContractExperimentRunner().writeJson(report, output);
            String json = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
            assertTrue(json.startsWith("{\"experiment_id\":\"contract-20\""));
            assertTrue(json.contains("\"passed\":20"));
        } finally {
            Files.deleteIfExists(output);
        }
    }

    @Test
    public void writesPerCaseRequestResponseAndTraceArtifacts() throws Exception {
        Path caseFile = Paths.get("..", "experiments", "contract-20", "contract-20.csv").toAbsolutePath().normalize();
        ExperimentRunReport report = new ContractExperimentRunner().runFromCsv(caseFile, 20260902L);
        Path output = Files.createTempDirectory("contract-20-run-");
        try {
            new ContractExperimentRunner().writeArtifacts(report, output);
            assertTrue(Files.exists(output.resolve("manifest.json")));
            assertTrue(Files.exists(output.resolve("C-01").resolve("request.json")));
            assertTrue(Files.exists(output.resolve("C-01").resolve("response.json")));
            assertTrue(Files.exists(output.resolve("C-01").resolve("trace.json")));
            assertTrue(Files.exists(output.resolve("C-01").resolve("sha256.json")));
            assertTrue(new String(Files.readAllBytes(output.resolve("C-01").resolve("trace.json")), StandardCharsets.UTF_8)
                    .contains("trace-C-01"));
        } finally {
            deleteRecursively(output);
        }
    }

    private static void deleteRecursively(Path path) throws Exception {
        if (Files.isDirectory(path)) {
            try (java.nio.file.DirectoryStream<Path> children = Files.newDirectoryStream(path)) {
                for (Path child : children) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
