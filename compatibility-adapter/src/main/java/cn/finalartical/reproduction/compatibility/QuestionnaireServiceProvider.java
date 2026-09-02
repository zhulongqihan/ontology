package cn.finalartical.reproduction.compatibility;

import cn.finalartical.reproduction.ontology.JobOntologyDetail;
import cn.finalartical.reproduction.ontology.OntologyAssembler;
import cn.finalartical.reproduction.ontology.Questionnaire;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class QuestionnaireServiceProvider implements QuestionnaireProvider {
    private final QuestionnaireRepository repository;
    private final OntologyAssembler assembler;

    public QuestionnaireServiceProvider(QuestionnaireRepository repository, OntologyAssembler assembler) {
        if (repository == null || assembler == null) {
            throw new IllegalArgumentException("repository and assembler must not be null");
        }
        this.repository = repository;
        this.assembler = assembler;
    }

    @Override
    public OperationResult<List<String>> queryQuestionnaireIdsBySubjectId(String subjectId, String traceId) {
        if (isInvalid(subjectId)) {
            return OperationResult.of(OperationStatus.INVALID_INPUT, "subject_id is invalid", traceId, Collections.<String>emptyList());
        }
        List<Questionnaire> questionnaires = isBlank(subjectId)
                ? repository.findAll()
                : repository.findBySubjectId(subjectId);
        List<String> ids = new ArrayList<String>();
        for (Questionnaire questionnaire : questionnaires) {
            ids.add(questionnaire.getId());
        }
        OperationStatus status = ids.isEmpty() ? OperationStatus.EMPTY : OperationStatus.SUCCESS;
        return OperationResult.of(status, status == OperationStatus.EMPTY ? "no questionnaire found" : "ok", traceId, ids);
    }

    @Override
    public OperationResult<QuestionnaireLinkageConfig> queryQuestionnaireLinkageConfig(String questionnaireId, String traceId) {
        if (isInvalid(questionnaireId)) {
            return OperationResult.of(OperationStatus.INVALID_INPUT, "questionnaire_id is invalid", traceId, null);
        }
        if (isBlank(questionnaireId)) {
            return OperationResult.of(OperationStatus.NOT_FOUND, "linkage config not found", traceId, null);
        }
        Optional<QuestionnaireLinkageConfig> config = repository.findLinkageConfig(questionnaireId);
        if (!config.isPresent()) {
            return OperationResult.of(OperationStatus.NOT_FOUND, "linkage config not found", traceId, null);
        }
        return OperationResult.of(OperationStatus.SUCCESS, "ok", traceId, config.get());
    }

    @Override
    public OperationResult<QuestionnaireLinkageConfig> saveQuestionnaireLinkageConfig(String questionnaireId, String version, String traceId) {
        if (isInvalid(questionnaireId) || isBlank(questionnaireId) || isBlank(version)) {
            return OperationResult.of(OperationStatus.INVALID_INPUT, "linkage config is invalid", traceId, null);
        }
        if (!repository.findById(questionnaireId).isPresent()) {
            return OperationResult.of(OperationStatus.NOT_FOUND, "questionnaire not found", traceId, null);
        }
        QuestionnaireLinkageConfig config = new QuestionnaireLinkageConfig(questionnaireId, version);
        repository.saveLinkageConfig(config);
        return OperationResult.of(OperationStatus.SUCCESS, "saved", traceId, config);
    }

    @Override
    public OperationResult<JobOntologyDetail> questionnaireDetail(String questionnaireId, String traceId) {
        if (isInvalid(questionnaireId)) {
            return OperationResult.of(OperationStatus.INVALID_INPUT, "questionnaire_id is invalid", traceId, null);
        }
        if (isBlank(questionnaireId)) {
            return OperationResult.of(OperationStatus.NOT_FOUND, "questionnaire not found", traceId, null);
        }
        Optional<Questionnaire> questionnaire = repository.findById(questionnaireId);
        if (!questionnaire.isPresent()) {
            return OperationResult.of(OperationStatus.NOT_FOUND, "questionnaire not found", traceId, null);
        }
        JobOntologyDetail detail = assembler.assembleQuestionnaire(questionnaire.get(), 1);
        return OperationResult.of(OperationStatus.SUCCESS, "ok", traceId, detail);
    }

    private static boolean isInvalid(String value) {
        return value != null && value.startsWith("!");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
