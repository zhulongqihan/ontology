package cn.finalartical.reproduction.admin;

public class OntologyRelationConfig {
    private String name;
    private String targetType;
    private String cardinality;

    public OntologyRelationConfig() {
    }

    public OntologyRelationConfig(String name, String targetType, String cardinality) {
        this.name = name;
        this.targetType = targetType;
        this.cardinality = cardinality;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getCardinality() {
        return cardinality;
    }

    public void setCardinality(String cardinality) {
        this.cardinality = cardinality;
    }
}
