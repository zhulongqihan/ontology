package cn.finalartical.reproduction.compatibility;

import cn.finalartical.reproduction.ontology.JobOntologyDetail;

import java.util.List;

public interface QuestionnaireProvider {
    OperationResult<List<String>> queryQuestionnaireIdsBySubjectId(String subjectId, String traceId);

    OperationResult<QuestionnaireLinkageConfig> queryQuestionnaireLinkageConfig(String questionnaireId, String traceId);

    OperationResult<QuestionnaireLinkageConfig> saveQuestionnaireLinkageConfig(String questionnaireId, String version, String traceId);

    OperationResult<JobOntologyDetail> questionnaireDetail(String questionnaireId, String traceId);
}
