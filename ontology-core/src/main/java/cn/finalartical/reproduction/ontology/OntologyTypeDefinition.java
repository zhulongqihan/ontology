package cn.finalartical.reproduction.ontology;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class OntologyTypeDefinition {
    private final String id;
    private final String label;
    private final List<String> fixedAttributes;
    private final List<String> dynamicAttributes;
    private final Map<String, OntologyRelationDefinition> relations;

    public OntologyTypeDefinition(String id, String label, List<String> fixedAttributes,
                                  List<String> dynamicAttributes,
                                  List<OntologyRelationDefinition> relationDefinitions) {
        if (isBlank(id) || isBlank(label)) {
            throw new IllegalArgumentException("ontology type definition values must not be blank");
        }
        this.id = id;
        this.label = label;
        this.fixedAttributes = immutableCopy(fixedAttributes);
        this.dynamicAttributes = immutableCopy(dynamicAttributes);
        this.relations = new LinkedHashMap<String, OntologyRelationDefinition>();
        if (relationDefinitions != null) {
            for (OntologyRelationDefinition relation : relationDefinitions) {
                if (relation == null) {
                    throw new IllegalArgumentException("ontology relation definition must not be null");
                }
                if (this.relations.put(relation.getName(), relation) != null) {
                    throw new IllegalArgumentException("duplicate ontology relation: " + relation.getName());
                }
            }
        }
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public List<String> getFixedAttributes() {
        return fixedAttributes;
    }

    public List<String> getDynamicAttributes() {
        return dynamicAttributes;
    }

    public OntologyRelationDefinition relation(String name) {
        return relations.get(name);
    }

    public boolean matches(String candidate) {
        return id.equals(candidate) || label.equalsIgnoreCase(candidate);
    }

    public boolean hasFixedAttribute(String name) {
        return fixedAttributes.contains(name);
    }

    public boolean hasDynamicAttribute(String name) {
        for (String pattern : dynamicAttributes) {
            if (pattern.equals(name) || wildcardPattern(pattern).matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasDeclaredAttribute(String name) {
        return hasFixedAttribute(name) || hasDynamicAttribute(name);
    }

    public Map<String, OntologyRelationDefinition> getRelations() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, OntologyRelationDefinition>(relations));
    }

    private static Pattern wildcardPattern(String value) {
        StringBuilder expression = new StringBuilder("^");
        String[] segments = value.split("\\.", -1);
        for (int index = 0; index < segments.length; index++) {
            if (index > 0) {
                expression.append("\\.");
            }
            expression.append("*".equals(segments[index]) ? "[^.]+" : Pattern.quote(segments[index]));
        }
        return Pattern.compile(expression.append("$").toString());
    }

    private static List<String> immutableCopy(List<String> values) {
        List<String> result = new ArrayList<String>();
        if (values != null) {
            for (String value : values) {
                if (isBlank(value)) {
                    throw new IllegalArgumentException("ontology attribute name must not be blank");
                }
                result.add(value);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
