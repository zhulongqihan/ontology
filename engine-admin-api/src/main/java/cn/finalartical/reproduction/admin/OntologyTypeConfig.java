package cn.finalartical.reproduction.admin;

import java.util.ArrayList;
import java.util.List;

public class OntologyTypeConfig {
    private String id;
    private String label;
    private String description;
    private List<String> fixedAttributes = new ArrayList<String>();
    private List<String> dynamicAttributes = new ArrayList<String>();
    private List<OntologyRelationConfig> relations = new ArrayList<OntologyRelationConfig>();

    public OntologyTypeConfig() {
    }

    public OntologyTypeConfig(String id, String label, String description) {
        this.id = id;
        this.label = label;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getFixedAttributes() {
        return fixedAttributes;
    }

    public void setFixedAttributes(List<String> fixedAttributes) {
        this.fixedAttributes = fixedAttributes == null ? new ArrayList<String>() : fixedAttributes;
    }

    public List<String> getDynamicAttributes() {
        return dynamicAttributes;
    }

    public void setDynamicAttributes(List<String> dynamicAttributes) {
        this.dynamicAttributes = dynamicAttributes == null ? new ArrayList<String>() : dynamicAttributes;
    }

    public List<OntologyRelationConfig> getRelations() {
        return relations;
    }

    public void setRelations(List<OntologyRelationConfig> relations) {
        this.relations = relations == null ? new ArrayList<OntologyRelationConfig>() : relations;
    }
}
