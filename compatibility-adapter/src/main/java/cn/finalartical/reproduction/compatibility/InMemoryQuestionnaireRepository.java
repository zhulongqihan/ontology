package cn.finalartical.reproduction.compatibility;

import cn.finalartical.reproduction.ontology.Questionnaire;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryQuestionnaireRepository implements QuestionnaireRepository {
    private final Map<String, Questionnaire> questionnaires = new LinkedHashMap<String, Questionnaire>();
    private final Map<String, QuestionnaireLinkageConfig> linkageConfigs = new LinkedHashMap<String, QuestionnaireLinkageConfig>();

    public InMemoryQuestionnaireRepository add(Questionnaire questionnaire) {
        if (questionnaire == null) {
            throw new IllegalArgumentException("questionnaire must not be null");
        }
        questionnaires.put(questionnaire.getId(), questionnaire);
        return this;
    }

    @Override
    public List<Questionnaire> findBySubjectId(String subjectId) {
        List<Questionnaire> result = new ArrayList<Questionnaire>();
        for (Questionnaire questionnaire : questionnaires.values()) {
            if (questionnaire.getSubjectId().equals(subjectId)) {
                result.add(questionnaire);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public List<Questionnaire> findAll() {
        return Collections.unmodifiableList(new ArrayList<Questionnaire>(questionnaires.values()));
    }

    @Override
    public Optional<Questionnaire> findById(String questionnaireId) {
        return Optional.ofNullable(questionnaires.get(questionnaireId));
    }

    @Override
    public Optional<QuestionnaireLinkageConfig> findLinkageConfig(String questionnaireId) {
        return Optional.ofNullable(linkageConfigs.get(questionnaireId));
    }

    @Override
    public void saveLinkageConfig(QuestionnaireLinkageConfig config) {
        linkageConfigs.put(config.getQuestionnaireId(), config);
    }
}
