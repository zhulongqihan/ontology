package cn.finalartical.reproduction.ontology;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OntologyAssemblerTest {
    @Test
    public void assemblesFixedDynamicAttributesAndRelations() {
        Questionnaire questionnaire = new Questionnaire("q-001", "Java 面试基础", "subject-001")
                .addSubject(new Subject("s-001", "集合").addOption(new Option("o-001", "List")));

        JobOntologyDetail detail = new OntologyAssembler().assembleQuestionnaire(questionnaire, 1);

        assertEquals("Questionnaire", detail.getObjectType());
        assertEquals("Java 面试基础", detail.getFixedAttributes().get("name"));
        assertEquals(1, detail.getDynamicAttributes().get("subjectCount"));
        assertEquals(2, detail.getRelations().size());
        assertTrue(detail.getRelations().toString().contains("containsSubject"));
    }

    @Test
    public void relationIsIdempotent() {
        JobOntologyDetail detail = new JobOntologyDetail("Questionnaire", "q-001", 1);
        OntologyRelation relation = new OntologyRelation("containsSubject", "Subject", "s-001");

        detail.addRelation(relation).addRelation(relation);

        assertEquals(1, detail.getRelations().size());
    }
}
