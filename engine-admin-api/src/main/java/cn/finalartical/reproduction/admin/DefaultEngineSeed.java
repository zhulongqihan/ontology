package cn.finalartical.reproduction.admin;

import java.util.Arrays;

public final class DefaultEngineSeed {
    private DefaultEngineSeed() {
    }

    public static EngineState create() {
        EngineState state = new EngineState();
        state.setEngineId("flexible-engine-ontology");
        state.setEngineName("柔性引擎与本体化平台");
        state.setEngineVersion("0.3.0");
        state.setUpdatedAt("2026-09-02T00:00:00Z");

        EngineModel interview = new EngineModel(
                "interview-session",
                "面试会话",
                "用于演示动态字段、评价流程和上下文状态的柔性对象模型。",
                2,
                "PENDING_INTERVIEW");
        interview.getFields().add(new EngineField("candidateName", "STRING", true, 1, null));
        interview.getFields().add(new EngineField("score", "INTEGER", false, 1, null));
        interview.getFields().add(new EngineField("evaluationScore", "INTEGER", false, 2, null));
        interview.getFields().add(new EngineField("remote", "BOOLEAN", false, 2, false));
        interview.setStates(Arrays.asList("PENDING_INTERVIEW", "IN_INTERVIEW", "COMPLETED"));
        interview.getTransitions().add(new EngineTransition("PENDING_INTERVIEW", "startInterview", "IN_INTERVIEW"));
        interview.getTransitions().add(new EngineTransition("IN_INTERVIEW", "submitEvaluation", "COMPLETED"));
        interview.setUpdatedAt(state.getUpdatedAt());
        state.getModels().add(interview);

        EngineModel questionnaire = new EngineModel(
                "questionnaire",
                "Questionnaire",
                "本体化问卷对象的固定属性、动态属性和关系入口。",
                1,
                "DRAFT");
        questionnaire.getFields().add(new EngineField("name", "STRING", true, 1, null));
        questionnaire.getFields().add(new EngineField("subjectId", "STRING", true, 1, null));
        questionnaire.getFields().add(new EngineField("subjectCount", "INTEGER", false, 1, 0));
        questionnaire.setStates(Arrays.asList("DRAFT", "PUBLISHED", "ARCHIVED"));
        questionnaire.getTransitions().add(new EngineTransition("DRAFT", "publish", "PUBLISHED"));
        questionnaire.getTransitions().add(new EngineTransition("PUBLISHED", "archive", "ARCHIVED"));
        questionnaire.setUpdatedAt(state.getUpdatedAt());
        state.getModels().add(questionnaire);

        OntologyTypeConfig questionnaireType = new OntologyTypeConfig(
                "questionnaire", "Questionnaire", "面试套卷本体对象");
        questionnaireType.setFixedAttributes(Arrays.asList("name", "subjectId"));
        questionnaireType.setDynamicAttributes(Arrays.asList("subjects", "subjectCount", "subject.*.title", "subject.*.optionCount"));
        questionnaireType.getRelations().add(new OntologyRelationConfig("containsSubject", "Subject", "1:N"));
        state.getOntologyTypes().add(questionnaireType);

        OntologyTypeConfig subjectType = new OntologyTypeConfig("subject", "Subject", "套卷题目对象");
        subjectType.setFixedAttributes(Arrays.asList("title"));
        subjectType.setDynamicAttributes(Arrays.asList("optionCount"));
        subjectType.getRelations().add(new OntologyRelationConfig("subjectContainsOption", "Option", "1:N"));
        state.getOntologyTypes().add(subjectType);
        OntologyTypeConfig optionType = new OntologyTypeConfig("option", "Option", "题目选项对象");
        optionType.setFixedAttributes(Arrays.asList("label"));
        state.getOntologyTypes().add(optionType);

        state.getServices().add(new ServiceRegistration(
                "questionnaire-provider",
                "Questionnaire Provider",
                "LocalServiceRegistry",
                "READY",
                "local://questionnaire-provider",
                "v1"));
        state.getServices().add(new ServiceRegistration(
                "ontology-assembler",
                "Ontology Detail Assembler",
                "LocalOntologyProvider",
                "READY",
                "local://ontology-assembler",
                "v1"));
        return state;
    }
}
