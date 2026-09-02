package cn.finalartical.reproduction.ontology;

import java.util.Locale;

/**
 * Parses the small cardinality vocabulary used by the ontology registry.
 *
 * <p>The right-hand side describes the number of targets allowed for one
 * source object.  {@code N} and {@code *} mean an unbounded collection;
 * numeric values describe an exact target count.</p>
 */
public final class OntologyCardinality {
    private final String expression;
    private final String sourceMultiplicity;
    private final String targetMultiplicity;

    private OntologyCardinality(String expression, String sourceMultiplicity, String targetMultiplicity) {
        this.expression = expression;
        this.sourceMultiplicity = sourceMultiplicity;
        this.targetMultiplicity = targetMultiplicity;
    }

    public static OntologyCardinality parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("ontology cardinality must not be blank");
        }
        String[] parts = value.trim().toUpperCase(Locale.ROOT).split(":", -1);
        if (parts.length != 2 || !validMultiplicity(parts[0]) || !validMultiplicity(parts[1])) {
            throw new IllegalArgumentException("unsupported ontology cardinality: " + value);
        }
        String source = normalize(parts[0]);
        String target = normalize(parts[1]);
        return new OntologyCardinality(source + ":" + target, source, target);
    }

    public String getExpression() {
        return expression;
    }

    public String getSourceMultiplicity() {
        return sourceMultiplicity;
    }

    public String getTargetMultiplicity() {
        return targetMultiplicity;
    }

    public boolean allowsTargetCount(int count) {
        if (count < 0) {
            return false;
        }
        if ("N".equals(targetMultiplicity)) {
            return true;
        }
        return Integer.parseInt(targetMultiplicity) == count;
    }

    public boolean allowsSourceCount(int count) {
        if (count < 0) {
            return false;
        }
        if ("N".equals(sourceMultiplicity)) {
            return true;
        }
        return Integer.parseInt(sourceMultiplicity) == count;
    }

    private static boolean validMultiplicity(String value) {
        if ("N".equals(value) || "*".equals(value)) {
            return true;
        }
        try {
            return Integer.parseInt(value) >= 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static String normalize(String value) {
        return "*".equals(value) ? "N" : value;
    }
}
