package cn.finalartical.reproduction.ontology;

public final class OntologyRelationDefinition {
    private final String name;
    private final String targetType;
    private final OntologyCardinality cardinality;

    public OntologyRelationDefinition(String name, String targetType, String cardinality) {
        if (isBlank(name) || isBlank(targetType)) {
            throw new IllegalArgumentException("ontology relation definition values must not be blank");
        }
        this.name = name;
        this.targetType = targetType;
        this.cardinality = OntologyCardinality.parse(cardinality);
    }

    public String getName() {
        return name;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getCardinality() {
        return cardinality.getExpression();
    }

    public boolean allowsTargetCount(int count) {
        return cardinality.allowsTargetCount(count);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
