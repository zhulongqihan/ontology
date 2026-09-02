package cn.finalartical.reproduction.admin;

import cn.finalartical.reproduction.ontology.OntologyGraphValidator;
import cn.finalartical.reproduction.ontology.OntologyTypeDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LocalOntologyProvider implements OntologyProvider {
    @Override
    public Map<String, Object> assemble(String modelId, String ontologyTypeId, String contextId, Map<String, Object> values,
                                        Object input, List<OntologyTypeDefinition> definitions) {
        if (input == null && !(values.get("subjects") instanceof List)) {
            return new LinkedHashMap<String, Object>();
        }
        Map<String, Object> graph = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> objects = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> relations = new ArrayList<Map<String, Object>>();
        Map<String, Object> rootAttributes = new LinkedHashMap<String, Object>(values);
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("id", contextId);
        if (ontologyTypeId == null || ontologyTypeId.trim().isEmpty()) {
            throw new IllegalArgumentException("model has no explicit ontology binding: " + modelId);
        }
        root.put("type", ontologyTypeId(ontologyTypeId, definitions));
        root.put("attributes", rootAttributes);
        objects.add(root);
        Object subjects = values.get("subjects");
        if (subjects instanceof List) {
            rootAttributes.put("subjectCount", ((List<?>) subjects).size());
            for (Object subjectItem : (List<?>) subjects) {
                if (!(subjectItem instanceof Map)) {
                    throw new IllegalArgumentException("ontology subjects must be objects");
                }
                Map<?, ?> source = (Map<?, ?>) subjectItem;
                String subjectId = textValue(source.get("id"), "").trim();
                String title = textValue(source.get("title"), "").trim();
                if (subjectId.isEmpty() || title.isEmpty()) {
                    throw new IllegalArgumentException("ontology subject id and title are required");
                }
                Map<String, Object> subject = new LinkedHashMap<String, Object>();
                subject.put("id", subjectId);
                subject.put("type", ontologyTypeId("subject", definitions));
                Map<String, Object> attributes = new LinkedHashMap<String, Object>();
                attributes.put("title", title);
                attributes.put("optionCount", optionsCount(source.get("options")));
                subject.put("attributes", attributes);
                objects.add(subject);
                relations.add(edge(contextId, "containsSubject", subjectId));
                rootAttributes.put("subject." + subjectId + ".title", title);
                rootAttributes.put("subject." + subjectId + ".optionCount",
                        optionsCount(source.get("options")));
                Object options = source.get("options");
                if (options instanceof List) {
                    for (Object optionItem : (List<?>) options) {
                        if (!(optionItem instanceof Map)) {
                            throw new IllegalArgumentException("ontology options must be objects");
                        }
                        Map<?, ?> optionSource = (Map<?, ?>) optionItem;
                        String optionId = textValue(optionSource.get("id"), "").trim();
                        String label = textValue(optionSource.get("label"), "").trim();
                        if (optionId.isEmpty() || label.isEmpty()) {
                            throw new IllegalArgumentException("ontology option id and label are required");
                        }
                        Map<String, Object> option = new LinkedHashMap<String, Object>();
                        option.put("id", optionId);
                        option.put("type", ontologyTypeId("option", definitions));
                        option.put("attributes", Collections.singletonMap("label", label));
                        objects.add(option);
                        relations.add(edge(subjectId, "subjectContainsOption", optionId));
                    }
                }
            }
        }
        graph.put("rootObjectId", contextId);
        graph.put("objects", objects);
        graph.put("relations", relations);
        new OntologyGraphValidator().validate(graph, definitions);
        return graph;
    }

    private String ontologyTypeId(String requested, List<OntologyTypeDefinition> definitions) {
        for (OntologyTypeDefinition type : definitions) {
            if (requested.equals(type.getId()) || (type.getLabel() != null && requested.equalsIgnoreCase(type.getLabel()))) {
                return type.getId();
            }
        }
        throw new IllegalArgumentException("ontology type not found: " + requested);
    }

    private Map<String, Object> edge(String sourceId, String relation, String targetId) {
        Map<String, Object> edge = new LinkedHashMap<String, Object>();
        edge.put("sourceId", sourceId);
        edge.put("relation", relation);
        edge.put("targetId", targetId);
        return edge;
    }

    private int optionsCount(Object options) {
        return options instanceof List ? ((List<?>) options).size() : 0;
    }

    private static String textValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }
}
