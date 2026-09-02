package cn.finalartical.reproduction.compatibility;

import cn.finalartical.reproduction.ontology.JobOntologyDetail;

import java.util.Collections;
import java.util.List;

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

    public OperationResult<QuestionnaireLinkageConfig> queryQuestionnaireLinkageConfig(String questionnaireId, String traceId) {
        return provider.queryQuestionnaireLinkageConfig(questionnaireId, traceId);
    }

    public OperationResult<QuestionnaireLinkageConfig> saveQuestionnaireLinkageConfig(String questionnaireId, String version, String traceId) {
        return provider.saveQuestionnaireLinkageConfig(questionnaireId, version, traceId);
    }

    public OperationResult<JobOntologyDetail> questionnaireDetail(String questionnaireId, String traceId) {
        return provider.questionnaireDetail(questionnaireId, traceId);
    }

    public static <T> OperationResult<T> providerUnavailable(String traceId) {
        return OperationResult.of(OperationStatus.ERROR, "provider unavailable", traceId, null);
    }
}
