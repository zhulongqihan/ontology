package cn.finalartical.reproduction.compatibility;

import cn.finalartical.reproduction.ontology.JobOntologyDetail;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class JsfExAssessService {
    private final QuestionnaireProvider provider;

    public JsfExAssessService(QuestionnaireProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        this.provider = provider;
    }

    public OperationResult<List<String>> queryQuestionnaireIdsBySubjectId(String subjectId, String traceId) {
        return provider.queryQuestionnaireIdsBySubjectId(subjectId, traceId);
    }

    /**
     * Compatibility entry point that accepts both the normalized request field
     * and the legacy field used by the historical JSF contract.
     */
    public OperationResult<List<String>> queryQuestionnaireIdsByRequest(Map<String, ?> request, String traceId) {
        return queryQuestionnaireIdsBySubjectId(requestValue(request, "subject_id", "legacy_subject_id"), traceId);
    }

    public OperationResult<QuestionnaireLinkageConfig> queryQuestionnaireLinkageConfig(String questionnaireId, String traceId) {
        return provider.queryQuestionnaireLinkageConfig(questionnaireId, traceId);
    }

    public OperationResult<QuestionnaireLinkageConfig> queryQuestionnaireLinkageConfigByRequest(Map<String, ?> request,
                                                                                                 String traceId) {
        return queryQuestionnaireLinkageConfig(requestValue(request, "questionnaire_id", "legacy_questionnaire_id"),
                traceId);
    }

    public OperationResult<QuestionnaireLinkageConfig> saveQuestionnaireLinkageConfig(String questionnaireId, String version, String traceId) {
        return provider.saveQuestionnaireLinkageConfig(questionnaireId, version, traceId);
    }

    public OperationResult<QuestionnaireLinkageConfig> saveQuestionnaireLinkageConfigByRequest(Map<String, ?> request,
                                                                                                String traceId) {
        return saveQuestionnaireLinkageConfig(
                requestValue(request, "questionnaire_id", "legacy_questionnaire_id"),
                requestValue(request, "version", "config"), traceId);
    }

    public OperationResult<JobOntologyDetail> questionnaireDetail(String questionnaireId, String traceId) {
        return provider.questionnaireDetail(questionnaireId, traceId);
    }

    public OperationResult<JobOntologyDetail> questionnaireDetailByRequest(Map<String, ?> request, String traceId) {
        return questionnaireDetail(requestValue(request, "questionnaire_id", "legacy_questionnaire_id"), traceId);
    }

    public static <T> OperationResult<T> providerUnavailable(String traceId) {
        return OperationResult.of(OperationStatus.ERROR, "provider unavailable", traceId, null);
    }

    private static String requestValue(Map<String, ?> request, String normalizedName, String legacyName) {
        if (request == null) {
            return null;
        }
        if (request.containsKey(normalizedName)) {
            return textValue(request.get(normalizedName));
        }
        if (request.containsKey(legacyName)) {
            return textValue(request.get(legacyName));
        }
        return null;
    }

    private static String textValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
