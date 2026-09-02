package cn.finalartical.reproduction.ontology;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JobOntologyDetail {
    private final String objectType;
    private final String objectId;
    private final int sourceVersion;
    private final Map<String, Object> fixedAttributes = new LinkedHashMap<String, Object>();
    private final Map<String, Object> dynamicAttributes = new LinkedHashMap<String, Object>();
    private final List<OntologyRelation> relations = new ArrayList<OntologyRelation>();

    public JobOntologyDetail(String objectType, String objectId, int sourceVersion) {
        if (isBlank(objectType) || isBlank(objectId) || sourceVersion < 1) {
            throw new IllegalArgumentException("ontology detail values are invalid");
        }
        this.objectType = objectType;
        this.objectId = objectId;
        this.sourceVersion = sourceVersion;
    }

    public JobOntologyDetail putFixed(String name, Object value) {
        fixedAttributes.put(requireName(name), value);
        return this;
    }

    public JobOntologyDetail putDynamic(String name, Object value) {
        dynamicAttributes.put(requireName(name), value);
        return this;
    }

    public JobOntologyDetail addRelation(OntologyRelation relation) {
        if (relation == null) {
            throw new IllegalArgumentException("relation must not be null");
        }
        if (!relations.contains(relation)) {
            relations.add(relation);
        }
        return this;
    }

    public String getObjectType() {
        return objectType;
    }

    public String getObjectId() {
        return objectId;
    }

    public int getSourceVersion() {
        return sourceVersion;
    }

    public Map<String, Object> getFixedAttributes() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, Object>(fixedAttributes));
    }

    public Map<String, Object> getDynamicAttributes() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, Object>(dynamicAttributes));
    }

    public List<OntologyRelation> getRelations() {
        return Collections.unmodifiableList(new ArrayList<OntologyRelation>(relations));
    }

    private static String requireName(String name) {
        if (isBlank(name)) {
            throw new IllegalArgumentException("attribute name must not be blank");
        }
        return name;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
