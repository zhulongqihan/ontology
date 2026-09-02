package cn.finalartical.reproduction.flexible;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class FlexibleEngine {
    private final List<FieldDefinition> schema;
    private final DynamicRecord record;
    private final WorkflowExecutor workflow;

    public FlexibleEngine(Collection<FieldDefinition> schema, WorkflowDefinition workflowDefinition) {
        this(schema, workflowDefinition, workflowDefinition == null ? null : workflowDefinition.getInitialState());
    }

    public FlexibleEngine(Collection<FieldDefinition> schema, WorkflowDefinition workflowDefinition, String initialState) {
        if (schema == null || workflowDefinition == null) {
            throw new IllegalArgumentException("schema and workflow must not be null");
        }
        this.schema = Collections.unmodifiableList(new ArrayList<FieldDefinition>(schema));
        this.record = new DynamicRecord();
        this.workflow = new WorkflowExecutor(workflowDefinition, initialState);
    }

    public FlexibleEngine set(String fieldName, Object value) {
        record.put(fieldName, value);
        return this;
    }

    public List<String> validate() {
        return record.validate(schema);
    }

    public List<String> validate(UnknownFieldPolicy unknownFieldPolicy) {
        return record.validate(schema, unknownFieldPolicy);
    }

    public Map<String, Object> values() {
        return record.asMap();
    }

    public String state() {
        return workflow.getCurrentState();
    }

    public String apply(String event) {
        return workflow.apply(event);
    }

    public List<String> stateHistory() {
        return workflow.getStateHistory();
    }
}
