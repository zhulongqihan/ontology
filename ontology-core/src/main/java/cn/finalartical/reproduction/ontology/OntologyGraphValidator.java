package cn.finalartical.reproduction.ontology;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates an instantiated ontology graph against the registry definition. */
public final class OntologyGraphValidator {
    public void validate(Map<String, Object> graph, Collection<OntologyTypeDefinition> definitions) {
        if (graph == null) {
            throw new IllegalArgumentException("ontology graph must not be null");
        }
        Map<String, OntologyTypeDefinition> types = indexTypes(definitions);
        List<?> objects = listValue(graph.get("objects"), "ontology graph objects");
        List<?> relations = listValue(graph.get("relations"), "ontology graph relations");
        Map<String, String> objectTypes = new HashMap<String, String>();
        Map<String, OntologyTypeDefinition> objectDefinitions = new HashMap<String, OntologyTypeDefinition>();
        for (Object item : objects) {
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException("ontology objects must be objects");
            }
            Map<?, ?> object = (Map<?, ?>) item;
            String id = requiredText(object.get("id"), "ontology object id");
            String typeName = requiredText(object.get("type"), "ontology object type");
            if (objectTypes.containsKey(id)) {
                throw new IllegalArgumentException("duplicate ontology object id: " + id);
            }
            OntologyTypeDefinition definition = findType(types, typeName);
            if (definition == null) {
                throw new IllegalArgumentException("ontology type not found: " + typeName);
            }
            Object attributesValue = object.get("attributes");
            if (attributesValue != null && !(attributesValue instanceof Map)) {
                throw new IllegalArgumentException("ontology object attributes must be an object: " + id);
            }
            Map<?, ?> attributes = attributesValue instanceof Map ? (Map<?, ?>) attributesValue : null;
            for (String fixed : definition.getFixedAttributes()) {
                if (attributes == null || !attributes.containsKey(fixed) || attributes.get(fixed) == null) {
                    throw new IllegalArgumentException("missing fixed ontology attribute " + fixed + " on " + id);
                }
            }
            if (attributes != null) {
                for (Object attributeName : attributes.keySet()) {
                    String name = requiredText(attributeName, "ontology attribute name");
                    if (!definition.hasDeclaredAttribute(name)) {
                        throw new IllegalArgumentException("undeclared ontology attribute " + name + " on " + id);
                    }
                }
            }
            objectTypes.put(id, definition.getId());
            objectDefinitions.put(id, definition);
        }

        String rootObjectId = requiredText(graph.get("rootObjectId"), "ontology graph rootObjectId");
        if (!objectTypes.containsKey(rootObjectId)) {
            throw new IllegalArgumentException("ontology graph root object not found: " + rootObjectId);
        }

        Set<String> edgeKeys = new HashSet<String>();
        Map<String, Integer> relationCounts = new HashMap<String, Integer>();
        Map<String, Integer> inverseRelationCounts = new HashMap<String, Integer>();
        for (Object item : relations) {
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException("ontology relations must be objects");
            }
            Map<?, ?> relation = (Map<?, ?>) item;
            String sourceId = requiredText(relation.get("sourceId"), "ontology relation sourceId");
            String relationName = requiredText(relation.get("relation"), "ontology relation name");
            String targetId = requiredText(relation.get("targetId"), "ontology relation targetId");
            if (!objectTypes.containsKey(sourceId)) {
                throw new IllegalArgumentException("ontology relation source not found: " + sourceId);
            }
            if (!objectTypes.containsKey(targetId)) {
                throw new IllegalArgumentException("ontology relation target not found: " + targetId);
            }
            String edgeKey = sourceId + "\u0000" + relationName + "\u0000" + targetId;
            if (!edgeKeys.add(edgeKey)) {
                throw new IllegalArgumentException("duplicate ontology relation: " + edgeKey);
            }
            OntologyTypeDefinition sourceDefinition = objectDefinitions.get(sourceId);
            OntologyRelationDefinition definition = sourceDefinition.relation(relationName);
            if (definition == null) {
                throw new IllegalArgumentException("ontology relation not defined for "
                        + sourceDefinition.getId() + ": " + relationName);
            }
            OntologyTypeDefinition targetDefinition = findType(types, definition.getTargetType());
            if (targetDefinition == null) {
                throw new IllegalArgumentException("ontology relation target type not found: "
                        + definition.getTargetType());
            }
            if (!targetDefinition.getId().equals(objectTypes.get(targetId))) {
                throw new IllegalArgumentException("ontology relation target type mismatch for "
                        + relationName + ": expected " + targetDefinition.getId()
                        + " but was " + objectTypes.get(targetId));
            }
            String countKey = sourceId + "\u0000" + relationName;
            relationCounts.put(countKey, relationCounts.containsKey(countKey)
                    ? relationCounts.get(countKey) + 1 : 1);
            String inverseCountKey = sourceDefinition.getId() + "\u0000" + relationName
                    + "\u0000" + targetId;
            inverseRelationCounts.put(inverseCountKey, inverseRelationCounts.containsKey(inverseCountKey)
                    ? inverseRelationCounts.get(inverseCountKey) + 1 : 1);
        }

        for (Map.Entry<String, OntologyTypeDefinition> object : objectDefinitions.entrySet()) {
            for (OntologyRelationDefinition definition : object.getValue().getRelations().values()) {
                String countKey = object.getKey() + "\u0000" + definition.getName();
                int actualTargets = relationCounts.containsKey(countKey) ? relationCounts.get(countKey) : 0;
                if (!definition.allowsTargetCount(actualTargets)) {
                    throw new IllegalArgumentException("ontology relation cardinality violated for "
                            + object.getKey() + ":" + definition.getName() + ", cardinality="
                            + definition.getCardinality() + ", actualTargets=" + actualTargets);
                }
                OntologyTypeDefinition targetDefinition = findType(types, definition.getTargetType());
                for (Map.Entry<String, String> candidate : objectTypes.entrySet()) {
                    if (!targetDefinition.getId().equals(candidate.getValue())) {
                        continue;
                    }
                    String inverseCountKey = object.getValue().getId() + "\u0000"
                            + definition.getName() + "\u0000" + candidate.getKey();
                    int actualSources = inverseRelationCounts.containsKey(inverseCountKey)
                            ? inverseRelationCounts.get(inverseCountKey) : 0;
                    if (!definition.allowsSourceCount(actualSources)) {
                        throw new IllegalArgumentException("ontology inverse cardinality violated for "
                                + candidate.getKey() + " via " + object.getKey() + ":"
                                + definition.getName() + ", cardinality=" + definition.getCardinality()
                                + ", actualSources=" + actualSources);
                    }
                }
            }
        }
    }

    private Map<String, OntologyTypeDefinition> indexTypes(Collection<OntologyTypeDefinition> definitions) {
        Map<String, OntologyTypeDefinition> result = new HashMap<String, OntologyTypeDefinition>();
        if (definitions == null) {
            return result;
        }
        for (OntologyTypeDefinition definition : definitions) {
            if (definition == null) {
                throw new IllegalArgumentException("ontology type definition must not be null");
            }
            if (result.put(definition.getId(), definition) != null) {
                throw new IllegalArgumentException("duplicate ontology type definition: " + definition.getId());
            }
        }
        return result;
    }

    private OntologyTypeDefinition findType(Map<String, OntologyTypeDefinition> types, String candidate) {
        OntologyTypeDefinition exact = types.get(candidate);
        if (exact != null) {
            return exact;
        }
        for (OntologyTypeDefinition definition : types.values()) {
            if (definition.matches(candidate)) {
                return definition;
            }
        }
        return null;
    }

    private static List<?> listValue(Object value, String name) {
        if (!(value instanceof List)) {
            throw new IllegalArgumentException(name + " must be an array");
        }
        return (List<?>) value;
    }

    private static String requiredText(Object value, String name) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return text;
    }
}
