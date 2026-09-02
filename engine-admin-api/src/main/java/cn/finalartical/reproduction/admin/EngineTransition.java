package cn.finalartical.reproduction.admin;

public class EngineTransition {
    private String fromState;
    private String event;
    private String toState;

    public EngineTransition() {
    }

    public EngineTransition(String fromState, String event, String toState) {
        this.fromState = fromState;
        this.event = event;
        this.toState = toState;
    }

    public String getFromState() {
        return fromState;
    }

    public void setFromState(String fromState) {
        this.fromState = fromState;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getToState() {
        return toState;
    }

    public void setToState(String toState) {
        this.toState = toState;
    }
}
