package cn.finalartical.reproduction.admin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A deliberately independent fixed-mapping comparator for the experiment.
 * It is not an implementation of the historical production system.
 */
final class RigidMappingBaseline {
    static final String MODE = "RIGID_MAPPING_BASELINE";

    Result execute(String modelId, String fromState, String event, Map<String, Object> inputValues,
                  Object ontologyInput) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        if (inputValues != null) {
            values.putAll(inputValues);
        }
        List<String> errors = new ArrayList<String>();
        String errorCode = null;
        String toState = fromState;
        String expectedFrom = null;
        String expectedEvent = null;
        String expectedTo = null;
        if ("questionnaire".equals(modelId)) {
            expectedFrom = "DRAFT";
            expectedEvent = "publish";
            expectedTo = "PUBLISHED";
        } else if ("interview-session".equals(modelId)) {
            expectedFrom = "PENDING_INTERVIEW";
            expectedEvent = "startInterview";
            expectedTo = "IN_INTERVIEW";
        } else {
            errors.add("rigid baseline has no fixed mapping for model: " + modelId);
            errorCode = "BASELINE_UNSUPPORTED_MODEL";
        }
        if (errors.isEmpty() && (!expectedFrom.equals(fromState) || !expectedEvent.equals(event))) {
            errors.add("rigid baseline does not support transition: " + fromState + " -> " + event);
            errorCode = "BASELINE_UNSUPPORTED_EVENT";
        }
        if (errors.isEmpty()) {
            validateFixedFields(modelId, values, errors);
            if (!errors.isEmpty()) {
                errorCode = "BASELINE_VALIDATION_ERROR";
            }
        }
        Map<String, Object> graph = new LinkedHashMap<String, Object>();
        if (errors.isEmpty() && (ontologyInput != null || values.get("subjects") instanceof List)) {
            try {
                graph = assembleFixedGraph(modelId, fromState, values);
            } catch (IllegalArgumentException exception) {
                errors.add(exception.getMessage());
                errorCode = "BASELINE_MAPPING_ERROR";
            }
        }
        boolean passed = errors.isEmpty();
        if (passed) {
            toState = expectedTo;
        }
        return new Result(passed, toState, values, graph, errors, errorCode);
    }

    private void validateFixedFields(String modelId, Map<String, Object> values, List<String> errors) {
        List<String> allowed = "questionnaire".equals(modelId)
                ? asList("name", "subjectId", "subjectCount", "subjects")
                : asList("candidateName", "score", "evaluationScore", "remote");
        for (String key : values.keySet()) {
            if (!allowed.contains(key)) {
                errors.add("rigid baseline does not map field: " + key);
            }
        }
        if ("questionnaire".equals(modelId)) {
            requireText(values, "name", errors);
            requireText(values, "subjectId", errors);
        } else {
            requireText(values, "candidateName", errors);
        }
    }

    private Map<String, Object> assembleFixedGraph(String modelId, String rootId, Map<String, Object> values) {
        Map<String, Object> graph = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> objects = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> relations = new ArrayList<Map<String, Object>>();
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("id", rootId);
        root.put("type", modelId);
        root.put("attributes", new LinkedHashMap<String, Object>(values));
        objects.add(root);
        Object subjects = values.get("subjects");
        if (subjects instanceof List) {
            for (Object subjectItem : (List<?>) subjects) {
                if (!(subjectItem instanceof Map)) {
                    throw new IllegalArgumentException("rigid baseline subjects must be objects");
                }
                Map<?, ?> source = (Map<?, ?>) subjectItem;
                String subjectId = text(source.get("id"));
                String title = text(source.get("title"));
                if (subjectId.isEmpty() || title.isEmpty()) {
                    throw new IllegalArgumentException("rigid baseline subject id and title are required");
                }
                Map<String, Object> subject = new LinkedHashMap<String, Object>();
                subject.put("id", subjectId);
                subject.put("type", "subject");
                subject.put("attributes", Collections.<String, Object>singletonMap("title", title));
                objects.add(subject);
                relations.add(edge(rootId, "containsSubject", subjectId));
                Object options = source.get("options");
                if (options instanceof List) {
                    for (Object optionItem : (List<?>) options) {
                        if (!(optionItem instanceof Map)) {
                            throw new IllegalArgumentException("rigid baseline options must be objects");
                        }
                        Map<?, ?> optionSource = (Map<?, ?>) optionItem;
                        String optionId = text(optionSource.get("id"));
                        String label = text(optionSource.get("label"));
                        if (optionId.isEmpty() || label.isEmpty()) {
                            throw new IllegalArgumentException("rigid baseline option id and label are required");
                        }
                        Map<String, Object> option = new LinkedHashMap<String, Object>();
                        option.put("id", optionId);
                        option.put("type", "option");
                        option.put("attributes", Collections.<String, Object>singletonMap("label", label));
                        objects.add(option);
                        relations.add(edge(subjectId, "subjectContainsOption", optionId));
                    }
                }
            }
        }
        graph.put("rootObjectId", rootId);
        graph.put("objects", objects);
        graph.put("relations", relations);
        return graph;
    }

    private Map<String, Object> edge(String sourceId, String relation, String targetId) {
        Map<String, Object> edge = new LinkedHashMap<String, Object>();
        edge.put("sourceId", sourceId);
        edge.put("relation", relation);
        edge.put("targetId", targetId);
        return edge;
    }

    private void requireText(Map<String, Object> values, String key, List<String> errors) {
        if (!values.containsKey(key) || text(values.get(key)).trim().isEmpty()) {
            errors.add("rigid baseline required field is missing: " + key);
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private List<String> asList(String... values) {
        List<String> result = new ArrayList<String>();
        Collections.addAll(result, values);
        return result;
    }

    static final class Result {
        final boolean passed;
        final String toState;
        final Map<String, Object> values;
        final Map<String, Object> graph;
        final List<String> errors;
        final String errorCode;

        Result(boolean passed, String toState, Map<String, Object> values, Map<String, Object> graph,
               List<String> errors, String errorCode) {
            this.passed = passed;
            this.toState = toState;
            this.values = values;
            this.graph = graph;
            this.errors = errors;
            this.errorCode = errorCode;
        }
    }
}
