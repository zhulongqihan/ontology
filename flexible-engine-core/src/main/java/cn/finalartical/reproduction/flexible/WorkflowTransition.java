package cn.finalartical.reproduction.flexible;

import java.util.Objects;

public final class WorkflowTransition {
    private final String fromState;
    private final String event;
    private final String toState;

    public WorkflowTransition(String fromState, String event, String toState) {
        if (isBlank(fromState) || isBlank(event) || isBlank(toState)) {
            throw new IllegalArgumentException("workflow transition values must not be blank");
        }
        this.fromState = fromState;
        this.event = event;
        this.toState = toState;
    }

    public String getFromState() {
        return fromState;
    }

    public String getEvent() {
        return event;
    }

    public String getToState() {
        return toState;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WorkflowTransition)) {
            return false;
        }
        WorkflowTransition that = (WorkflowTransition) other;
        return fromState.equals(that.fromState) && event.equals(that.event) && toState.equals(that.toState);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromState, event, toState);
    }
}
