package cn.finalartical.reproduction.compatibility;

import cn.finalartical.reproduction.ontology.Questionnaire;

import java.util.List;
import java.util.Optional;

public interface QuestionnaireRepository {
    List<Questionnaire> findBySubjectId(String subjectId);

    List<Questionnaire> findAll();

    Optional<Questionnaire> findById(String questionnaireId);

    Optional<QuestionnaireLinkageConfig> findLinkageConfig(String questionnaireId);

    void saveLinkageConfig(QuestionnaireLinkageConfig config);
}
