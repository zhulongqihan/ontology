package cn.finalartical.reproduction.admin;

import cn.finalartical.reproduction.flexible.ContextSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Produces the identity of the ontology definition used by a run. */
public final class OntologyDefinitionHasher {
    private OntologyDefinitionHasher() {
    }

    public static String sha256(OntologyTypeConfig type) {
        if (type == null || type.getId() == null || type.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("ontology type must have an id");
        }
        Map<String, Object> definition = new LinkedHashMap<String, Object>();
        definition.put("id", type.getId());
        definition.put("label", type.getLabel());
        definition.put("description", type.getDescription());
        definition.put("version", type.getVersion());

        List<String> fixed = new ArrayList<String>(type.getFixedAttributes());
        List<String> dynamic = new ArrayList<String>(type.getDynamicAttributes());
        Collections.sort(fixed);
        Collections.sort(dynamic);
        definition.put("fixedAttributes", fixed);
        definition.put("dynamicAttributes", dynamic);

        List<Map<String, Object>> relations = new ArrayList<Map<String, Object>>();
        for (OntologyRelationConfig relation : type.getRelations()) {
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("name", relation.getName());
            value.put("targetType", relation.getTargetType());
            value.put("cardinality", relation.getCardinality());
            relations.add(value);
        }
        Collections.sort(relations, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> left, Map<String, Object> right) {
                return String.valueOf(left.get("name")).compareTo(String.valueOf(right.get("name")));
            }
        });
        definition.put("relations", relations);
        return new ContextSnapshot(definition).getSha256();
    }
}
