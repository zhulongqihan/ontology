package cn.finalartical.reproduction.ontology;

public final class Option {
    private final String id;
    private final String label;

    public Option(String id, String label) {
        if (isBlank(id) || isBlank(label)) {
            throw new IllegalArgumentException("option id and label must not be blank");
        }
        this.id = id;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
