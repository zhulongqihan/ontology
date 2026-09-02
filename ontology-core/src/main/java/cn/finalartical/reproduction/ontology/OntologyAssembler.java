package cn.finalartical.reproduction.ontology;

import java.util.HashSet;
import java.util.Set;

public final class OntologyAssembler {
    public JobOntologyDetail assembleQuestionnaire(Questionnaire questionnaire, int sourceVersion) {
        if (questionnaire == null) {
            throw new IllegalArgumentException("questionnaire must not be null");
        }
        JobOntologyDetail detail = new JobOntologyDetail("Questionnaire", questionnaire.getId(), sourceVersion)
                .putFixed("name", questionnaire.getName())
                .putFixed("subjectId", questionnaire.getSubjectId())
                .putDynamic("subjectCount", questionnaire.getSubjects().size());

        Set<String> subjectIds = new HashSet<String>();
        Set<String> optionIds = new HashSet<String>();
        for (Subject subject : questionnaire.getSubjects()) {
            if (!subjectIds.add(subject.getId())) {
                throw new IllegalArgumentException("duplicate subject id: " + subject.getId());
            }
            detail.addRelation(new OntologyRelation("containsSubject", "Subject", subject.getId()));
            detail.putDynamic("subject." + subject.getId() + ".title", subject.getTitle());
            detail.putDynamic("subject." + subject.getId() + ".optionCount", subject.getOptions().size());
            for (Option option : subject.getOptions()) {
                if (!optionIds.add(option.getId())) {
                    throw new IllegalArgumentException("duplicate option id: " + option.getId());
                }
                detail.addRelation(new OntologyRelation("subjectContainsOption", "Option", option.getId()));
            }
        }
        return detail;
    }
}
