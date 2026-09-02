package cn.finalartical.reproduction.ontology;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OntologyGraphValidatorTest {
    @Test
    public void validatesObjectTypesTargetsAndOneToManyRelations() {
        OntologyGraphValidator validator = new OntologyGraphValidator();

        validator.validate(graph(1, "Subject", "Option"), Arrays.asList(
                type("questionnaire", "Questionnaire", attrs("name", "subjectId"),
                        relation("containsSubject", "Subject", "1:N")),
                type("subject", "Subject", attrs("title"),
                        relation("subjectContainsOption", "Option", "1:N")),
                type("option", "Option", Collections.<String>emptyList())
        ));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAConfiguredTargetTypeMismatch() {
        new OntologyGraphValidator().validate(graph(1, "Option", "Option"), Arrays.asList(
                type("questionnaire", "Questionnaire", attrs("name", "subjectId"),
                        relation("containsSubject", "Subject", "1:N")),
                type("subject", "Subject", attrs("title")),
                type("option", "Option", Collections.<String>emptyList())
        ));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMoreTargetsThanOneToOneAllows() {
        new OntologyGraphValidator().validate(graph(2, "Subject", "Option"), Arrays.asList(
                type("questionnaire", "Questionnaire", attrs("name", "subjectId"),
                        relation("containsSubject", "Subject", "1:1")),
                type("subject", "Subject", attrs("title"),
                        relation("subjectContainsOption", "Option", "1:N")),
                type("option", "Option", Collections.<String>emptyList())
        ));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingTargetForExactOneToOneRelation() {
        new OntologyGraphValidator().validate(graph(0, "Subject", "Option"), Arrays.asList(
                type("questionnaire", "Questionnaire", attrs("name", "subjectId"),
                        relation("containsSubject", "Subject", "1:1")),
                type("subject", "Subject", attrs("title"),
                        relation("subjectContainsOption", "Option", "1:N")),
                type("option", "Option", Collections.<String>emptyList())
        ));
    }

    @Test
    public void rejectsInverseCardinalityWhenOneTargetHasTwoSources() {
        OntologyTypeDefinition parent = new OntologyTypeDefinition("parent", "Parent",
                attrs("name"), Collections.<String>emptyList(),
                Arrays.asList(relation("contains", "child", "1:N")));
        OntologyTypeDefinition child = new OntologyTypeDefinition("child", "Child",
                attrs("name"), Collections.<String>emptyList(), Collections.<OntologyRelationDefinition>emptyList());
        Map<String, Object> graph = new LinkedHashMap<String, Object>();
        graph.put("rootObjectId", "p-1");
        graph.put("objects", Arrays.<Object>asList(
                object("p-1", "parent", attrsMap("name", "P1")),
                object("p-2", "parent", attrsMap("name", "P2")),
                object("c-1", "child", attrsMap("name", "C1"))));
        graph.put("relations", Arrays.<Object>asList(
                edge("p-1", "contains", "c-1"), edge("p-2", "contains", "c-1")));
        try {
            new OntologyGraphValidator().validate(graph, Arrays.asList(parent, child));
        } catch (IllegalArgumentException expected) {
            if (expected.getMessage().contains("inverse cardinality")) {
                return;
            }
            throw expected;
        }
        throw new AssertionError("inverse source multiplicity must be enforced");
    }

    private static OntologyTypeDefinition type(String id, String label, List<String> attributes,
                                                OntologyRelationDefinition... relations) {
        return new OntologyTypeDefinition(id, label, attributes,
                Collections.<String>emptyList(), Arrays.asList(relations));
    }

    private static List<String> attrs(String... values) {
        return Arrays.asList(values);
    }

    private static OntologyRelationDefinition relation(String name, String target, String cardinality) {
        return new OntologyRelationDefinition(name, target, cardinality);
    }

    private static Map<String, Object> graph(int subjectCount, String subjectType, String optionType) {
        Map<String, Object> graph = new LinkedHashMap<String, Object>();
        java.util.List<Map<String, Object>> objects = new java.util.ArrayList<Map<String, Object>>();
        objects.add(object("q-001", "questionnaire", attrsMap("name", "问卷", "subjectId", "s-001")));
        for (int index = 1; index <= subjectCount; index++) {
            String subjectId = "s-00" + index;
            objects.add(object(subjectId, subjectType, attrsMap("title", "题目" + index)));
        }
        objects.add(object("o-001", optionType, Collections.<String, Object>emptyMap()));
        java.util.List<Map<String, Object>> relations = new java.util.ArrayList<Map<String, Object>>();
        for (int index = 1; index <= subjectCount; index++) {
            relations.add(edge("q-001", "containsSubject", "s-00" + index));
        }
        if (subjectCount > 0) {
            relations.add(edge("s-001", "subjectContainsOption", "o-001"));
        }
        graph.put("rootObjectId", "q-001");
        graph.put("objects", objects);
        graph.put("relations", relations);
        return graph;
    }

    private static Map<String, Object> object(String id, String type, Map<String, Object> attributes) {
        Map<String, Object> object = new LinkedHashMap<String, Object>();
        object.put("id", id);
        object.put("type", type);
        object.put("attributes", attributes);
        return object;
    }

    private static Map<String, Object> attrsMap(String... values) {
        Map<String, Object> attributes = new LinkedHashMap<String, Object>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            attributes.put(values[index], values[index + 1]);
        }
        return attributes;
    }

    private static Map<String, Object> edge(String sourceId, String relation, String targetId) {
        Map<String, Object> edge = new LinkedHashMap<String, Object>();
        edge.put("sourceId", sourceId);
        edge.put("relation", relation);
        edge.put("targetId", targetId);
        return edge;
    }

}
