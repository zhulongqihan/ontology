package cn.finalartical.reproduction.ontology;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Subject {
    private final String id;
    private final String title;
    private final List<Option> options = new ArrayList<Option>();

    public Subject(String id, String title) {
        if (isBlank(id) || isBlank(title)) {
            throw new IllegalArgumentException("subject id and title must not be blank");
        }
        this.id = id;
        this.title = title;
    }

    public Subject addOption(Option option) {
        if (option == null) {
            throw new IllegalArgumentException("option must not be null");
        }
        options.add(option);
        return this;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public List<Option> getOptions() {
        return Collections.unmodifiableList(new ArrayList<Option>(options));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
