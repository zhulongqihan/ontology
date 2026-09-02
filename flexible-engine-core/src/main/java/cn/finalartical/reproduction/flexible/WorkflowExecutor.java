package cn.finalartical.reproduction.flexible;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WorkflowExecutor {
    private final WorkflowDefinition definition;
    private final List<String> stateHistory = new ArrayList<String>();
    private String currentState;

    public WorkflowExecutor(WorkflowDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("workflow definition must not be null");
        }
        this.definition = definition;
        this.currentState = definition.getInitialState();
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
