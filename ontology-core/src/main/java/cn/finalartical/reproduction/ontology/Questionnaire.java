package cn.finalartical.reproduction.ontology;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Questionnaire {
    private final String id;
    private final String name;
    private final String subjectId;
    private final List<Subject> subjects = new ArrayList<Subject>();

    public Questionnaire(String id, String name, String subjectId) {
        if (isBlank(id) || isBlank(name) || isBlank(subjectId)) {
            throw new IllegalArgumentException("questionnaire values must not be blank");
        }
        this.id = id;
        this.name = name;
        this.subjectId = subjectId;
    }

    public Questionnaire addSubject(Subject subject) {
        if (subject == null) {
            throw new IllegalArgumentException("subject must not be null");
        }
        subjects.add(subject);
        return this;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public List<Subject> getSubjects() {
        return Collections.unmodifiableList(new ArrayList<Subject>(subjects));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
