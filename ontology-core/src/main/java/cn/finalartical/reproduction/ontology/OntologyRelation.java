package cn.finalartical.reproduction.ontology;

import java.util.Objects;

public final class OntologyRelation {
    private final String relationType;
    private final String targetType;
    private final String targetId;

    public OntologyRelation(String relationType, String targetType, String targetId) {
        if (isBlank(relationType) || isBlank(targetType) || isBlank(targetId)) {
            throw new IllegalArgumentException("ontology relation values must not be blank");
        }
        this.relationType = relationType;
        this.targetType = targetType;
        this.targetId = targetId;
    }

    public String getRelationType() {
        return relationType;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OntologyRelation)) {
            return false;
        }
        OntologyRelation that = (OntologyRelation) other;
        return relationType.equals(that.relationType)
                && targetType.equals(that.targetType)
                && targetId.equals(that.targetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relationType, targetType, targetId);
    }

    @Override
    public String toString() {
        return relationType + "->" + targetType + ":" + targetId;
    }
}
