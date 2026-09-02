package cn.finalartical.reproduction.app;

import cn.finalartical.reproduction.compatibility.InMemoryQuestionnaireRepository;
import cn.finalartical.reproduction.compatibility.JsfExAssessService;
import cn.finalartical.reproduction.compatibility.OperationResult;
import cn.finalartical.reproduction.compatibility.QuestionnaireServiceProvider;
import cn.finalartical.reproduction.admin.EngineAdminServer;
import cn.finalartical.reproduction.admin.EngineStateRepository;
import cn.finalartical.reproduction.admin.JsonEngineStateRepository;
import cn.finalartical.reproduction.experiment.ContractExperimentRunner;
import cn.finalartical.reproduction.experiment.ExperimentRunReport;
import cn.finalartical.reproduction.flexible.ContextSnapshot;
import cn.finalartical.reproduction.flexible.FieldDefinition;
import cn.finalartical.reproduction.flexible.FieldType;
import cn.finalartical.reproduction.flexible.FlexibleEngine;
import cn.finalartical.reproduction.flexible.WorkflowDefinition;
import cn.finalartical.reproduction.flexible.WorkflowTransition;
import cn.finalartical.reproduction.ontology.OntologyAssembler;
import cn.finalartical.reproduction.ontology.Option;
import cn.finalartical.reproduction.ontology.Questionnaire;
import cn.finalartical.reproduction.ontology.Subject;
import cn.finalartical.reproduction.persistence.SqliteEngineStateRepository;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ReproductionApplication {
    private ReproductionApplication() {
    }

    public static void main(String[] args) {
        if (args.length > 0 && "admin".equals(args[0])) {
            runAdmin(args);
            return;
        }
        if (args.length > 0 && "contract".equals(args[0])) {
            runContractExperiment(args);
            return;
        }
        if (args.length > 0 && "experiments".equals(args[0])) {
            runExperiments(args);
            return;
        }

        FlexibleEngine engine = new FlexibleEngine(
                Arrays.asList(
                        new FieldDefinition("candidateName", FieldType.STRING, true, 1),
                        new FieldDefinition("score", FieldType.INTEGER, true, 1)),
                new WorkflowDefinition("PENDING_INTERVIEW", Arrays.asList(
                        new WorkflowTransition("PENDING_INTERVIEW", "startInterview", "IN_INTERVIEW"),
                        new WorkflowTransition("IN_INTERVIEW", "submitEvaluation", "COMPLETED"))));
        engine.set("candidateName", "小羊").set("score", 95);
        engine.apply("startInterview");

        Map<String, Object> context = new LinkedHashMap<String, Object>();
        context.put("candidateId", "candidate-001");
        context.put("state", engine.state());
        ContextSnapshot snapshot = new ContextSnapshot(context);

        Questionnaire questionnaire = new Questionnaire("q-001", "Java 面试基础", "subject-001")
                .addSubject(new Subject("s-001", "集合").addOption(new Option("o-001", "List")));
        InMemoryQuestionnaireRepository repository = new InMemoryQuestionnaireRepository().add(questionnaire);
        JsfExAssessService service = new JsfExAssessService(
                new QuestionnaireServiceProvider(repository, new OntologyAssembler()));
        OperationResult<?> detail = service.questionnaireDetail("q-001", "trace-demo");

        System.out.println(cn.finalartical.reproduction.admin.EngineAdminService.DATA_IDENTITY);
        System.out.println("workflow.state=" + engine.state());
        System.out.println("workflow.validationErrors=" + engine.validate());
        System.out.println("context.sha256=" + snapshot.getSha256());
        System.out.println("questionnaire.detail.status=" + detail.getStatus());
        System.out.println("questionnaire.detail.objectId=" + detail.getData().getClass().getSimpleName());
    }

    private static void runAdmin(String[] args) {
        try {
            int port = args.length > 1 ? Integer.parseInt(args[1]) : 8787;
            Path statePath = args.length > 2
                    ? Paths.get(args[2])
                    : Paths.get("data", "flexible-engine.db");
            Path legacyJsonPath = Paths.get("data", "engine-state.json");
            boolean importLegacy = args.length <= 3 || !"--no-legacy".equals(args[3]);
            EngineStateRepository repository = statePath.toString().toLowerCase().endsWith(".json")
                    ? new JsonEngineStateRepository(statePath)
                    : new SqliteEngineStateRepository(statePath, importLegacy ? legacyJsonPath : null);
            final EngineAdminServer server = EngineAdminServer.start(port, repository);
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    server.stop();
                }
            }));
            System.out.println("admin_url=http://127.0.0.1:" + server.getPort());
            System.out.println("state_path=" + statePath.toAbsolutePath());
            if (!statePath.toString().toLowerCase().endsWith(".json") && importLegacy) {
                System.out.println("legacy_json_path=" + legacyJsonPath.toAbsolutePath());
            }
            System.out.println("legacy_import=" + importLegacy);
            System.out.println("data_identity=" + cn.finalartical.reproduction.admin.EngineAdminService.DATA_IDENTITY);
            Thread.currentThread().join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            throw new IllegalStateException("admin server failed", exception);
        }
    }

    private static void runContractExperiment(String[] args) {
        try {
            Path cases = args.length > 1
                    ? Paths.get(args[1])
                    : Paths.get("experiments", "contract-20", "contract-20.csv");
            Path output = args.length > 2
                    ? Paths.get(args[2])
                    : Paths.get("runs", "contract-20", "latest");
            long seed = args.length > 3 ? Long.parseLong(args[3]) : 20260902L;
            ContractExperimentRunner runner = new ContractExperimentRunner();
            ExperimentRunReport report = runner.runFromCsv(cases, seed);
            runner.writeArtifacts(report, output);
            System.out.println("experiment=contract-20");
            System.out.println("data_identity=" + ExperimentRunReport.DATA_IDENTITY);
            System.out.println("total=" + report.getTotal());
            System.out.println("passed=" + report.getPassed());
            System.out.println("failed=" + report.getFailed());
            System.out.println("output=" + output.toAbsolutePath());
        } catch (Exception exception) {
            throw new IllegalStateException("contract experiment failed", exception);
        }
    }

    private static void runExperiments(String[] args) {
        try {
            Path cases = args.length > 1 ? Paths.get(args[1])
                    : Paths.get("experiments", "contract-20", "contract-20.csv");
            Path output = args.length > 2 ? Paths.get(args[2])
                    : Paths.get("runs", "reproduction-suite", "latest");
            Map<String, Object> report = new ReproductionExperimentSuite().run(cases, output);
            System.out.println("experiment=reproduction-abc");
            System.out.println("data_identity=ENGINE_EXPERIMENT_RESULT");
            System.out.println("output=" + output.toAbsolutePath());
            System.out.println("sections=" + report.keySet());
        } catch (Exception exception) {
            throw new IllegalStateException("reproduction experiments failed", exception);
        }
    }
}
