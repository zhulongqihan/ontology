package cn.finalartical.reproduction.ontology;

public final class OntologyAssembler {
    public JobOntologyDetail assembleQuestionnaire(Questionnaire questionnaire, int sourceVersion) {
        if (questionnaire == null) {
            throw new IllegalArgumentException("questionnaire must not be null");
        }
        JobOntologyDetail detail = new JobOntologyDetail("Questionnaire", questionnaire.getId(), sourceVersion)
                .putFixed("name", questionnaire.getName())
                .putFixed("subjectId", questionnaire.getSubjectId())
                .putDynamic("subjectCount", questionnaire.getSubjects().size());

        for (Subject subject : questionnaire.getSubjects()) {
            detail.addRelation(new OntologyRelation("containsSubject", "Subject", subject.getId()));
            detail.putDynamic("subject." + subject.getId() + ".title", subject.getTitle());
            detail.putDynamic("subject." + subject.getId() + ".optionCount", subject.getOptions().size());
            for (Option option : subject.getOptions()) {
                detail.addRelation(new OntologyRelation("subjectContainsOption", "Option", option.getId()));
            }
        }
        return detail;
    }
}
