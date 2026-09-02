package cn.finalartical.reproduction.flexible;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        List<WorkflowTransition> copied = new ArrayList<WorkflowTransition>();
        Set<String> transitionKeys = new LinkedHashSet<String>();
        for (WorkflowTransition transition : transitions) {
            if (transition == null) {
                throw new IllegalArgumentException("workflow transition must not be null");
            }
            String key = transition.getFromState() + "\u0000" + transition.getEvent();
            if (!transitionKeys.add(key)) {
                throw new IllegalArgumentException("duplicate workflow transition: "
                        + transition.getFromState() + " / " + transition.getEvent());
            }
            copied.add(transition);
        }
        this.transitions = Collections.unmodifiableList(copied);
    }

    public String getInitialState() {
        return initialState;
    }

    public List<WorkflowTransition> getTransitions() {
        return transitions;
    }

    public Set<String> getStates() {
        Set<String> states = new LinkedHashSet<String>();
        states.add(initialState);
        for (WorkflowTransition transition : transitions) {
            states.add(transition.getFromState());
            states.add(transition.getToState());
        }
        return Collections.unmodifiableSet(states);
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
