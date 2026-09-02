package cn.finalartical.reproduction.flexible;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WorkflowExecutor {
    private final WorkflowDefinition definition;
    private final List<String> stateHistory = new ArrayList<String>();
    private String currentState;

    public WorkflowExecutor(WorkflowDefinition definition) {
        this(definition, definition == null ? null : definition.getInitialState());
    }

    public WorkflowExecutor(WorkflowDefinition definition, String initialState) {
        if (definition == null) {
            throw new IllegalArgumentException("workflow definition must not be null");
        }
        if (initialState == null || initialState.trim().isEmpty()) {
            throw new IllegalArgumentException("runtime state must not be blank");
        }
        if (!definition.containsState(initialState)) {
            throw new IllegalArgumentException("runtime state is not part of workflow: " + initialState);
        }
        this.definition = definition;
        this.currentState = initialState;
        this.stateHistory.add(currentState);
    }

    public String getCurrentState() {
        return currentState;
    }

    public List<String> getStateHistory() {
        return Collections.unmodifiableList(new ArrayList<String>(stateHistory));
    }

    public String apply(String event) {
        WorkflowTransition transition = definition.find(currentState, event);
        if (transition == null) {
            throw new IllegalStateException("no transition from " + currentState + " on event " + event);
        }
        currentState = transition.getToState();
        stateHistory.add(currentState);
        return currentState;
    }
}
