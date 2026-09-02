package cn.finalartical.reproduction.flexible;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WorkflowDefinition {
    private final String initialState;
    private final List<WorkflowTransition> transitions;

    public WorkflowDefinition(String initialState, List<WorkflowTransition> transitions) {
        if (initialState == null || initialState.trim().isEmpty()) {
            throw new IllegalArgumentException("initial state must not be blank");
        }
        if (transitions == null) {
            throw new IllegalArgumentException("transitions must not be null");
        }
        this.initialState = initialState;
        this.transitions = Collections.unmodifiableList(new ArrayList<WorkflowTransition>(transitions));
    }

    public String getInitialState() {
        return initialState;
    }

    public List<WorkflowTransition> getTransitions() {
        return transitions;
    }

    public WorkflowTransition find(String fromState, String event) {
        for (WorkflowTransition transition : transitions) {
            if (transition.getFromState().equals(fromState) && transition.getEvent().equals(event)) {
                return transition;
            }
        }
        return null;
    }

    public boolean containsState(String state) {
        if (initialState.equals(state)) {
            return true;
        }
        for (WorkflowTransition transition : transitions) {
            if (transition.getFromState().equals(state) || transition.getToState().equals(state)) {
                return true;
            }
        }
        return false;
    }
}
