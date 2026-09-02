package cn.finalartical.reproduction.flexible;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FlexibleEngineTest {
    @Test
    public void validatesDynamicFieldsAndRequiredValues() {
        FlexibleEngine engine = new FlexibleEngine(
                Arrays.asList(
                        new FieldDefinition("candidateName", FieldType.STRING, true, 1),
                        new FieldDefinition("score", FieldType.INTEGER, true, 1),
                        new FieldDefinition("remote", FieldType.BOOLEAN, false, 1)),
                workflow());

        engine.set("candidateName", "小羊").set("score", "not-an-integer");

        assertEquals(1, engine.validate().size());
        assertTrue(engine.validate().get(0).contains("score"));
    }

    @Test
    public void movesThroughDeclaredWorkflowOnly() {
        FlexibleEngine engine = new FlexibleEngine(Arrays.<FieldDefinition>asList(), workflow());

        assertEquals("PENDING_INTERVIEW", engine.state());
        assertEquals("IN_INTERVIEW", engine.apply("startInterview"));
        assertEquals(Arrays.asList("PENDING_INTERVIEW", "IN_INTERVIEW"), engine.stateHistory());

        try {
            engine.apply("complete");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("no transition"));
            return;
        }
        assertFalse("an undeclared event must not be accepted", true);
    }

    @Test
    public void snapshotHashIsIndependentOfInsertionOrder() {
        Map<String, Object> first = new LinkedHashMap<String, Object>();
        first.put("b", 2);
        first.put("a", 1);
        Map<String, Object> second = new LinkedHashMap<String, Object>();
        second.put("a", 1);
        second.put("b", 2);

        assertEquals(new ContextSnapshot(first).getSha256(), new ContextSnapshot(second).getSha256());
    }

    @Test
    public void nestedSnapshotHashIsIndependentOfNestedInsertionOrder() {
        Map<String, Object> firstNested = new LinkedHashMap<String, Object>();
        firstNested.put("b", Arrays.asList(2, 3));
        firstNested.put("a", "nested");
        Map<String, Object> first = new LinkedHashMap<String, Object>();
        first.put("payload", firstNested);

        Map<String, Object> secondNested = new LinkedHashMap<String, Object>();
        secondNested.put("a", "nested");
        secondNested.put("b", Arrays.asList(2, 3));
        Map<String, Object> second = new LinkedHashMap<String, Object>();
        second.put("payload", secondNested);

        assertEquals(new ContextSnapshot(first).getSha256(), new ContextSnapshot(second).getSha256());
    }

    @Test
    public void runtimeContextProducesImmutableVersionedSnapshot() {
        RuntimeContext context = new RuntimeContext("ctx-1", "interview-session", 2, 1, "PENDING_INTERVIEW");
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("candidateName", "小羊");
        context.apply("IN_INTERVIEW", values, "run-1", ExecutionStatus.PASSED);

        ExecutionSnapshot snapshot = context.snapshot("2026-09-02T00:00:00Z");

        assertEquals("IN_INTERVIEW", snapshot.getState());
        assertEquals(1L, context.getRevision());
        assertEquals(64, snapshot.getSha256().length());
        try {
            snapshot.getValues().put("score", 95);
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("snapshot values must be immutable");
    }

    @Test
    public void workflowRejectsDuplicateEventFromTheSameState() {
        try {
            new WorkflowDefinition("DRAFT", Arrays.asList(
                    new WorkflowTransition("DRAFT", "publish", "PUBLISHED"),
                    new WorkflowTransition("DRAFT", "publish", "ARCHIVED")));
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("duplicate"));
            return;
        }
        throw new AssertionError("duplicate workflow event must be rejected");
    }

    @Test
    public void migratesRenamedFieldAndAddsDefaultValue() {
        VersionedSchema schemas = new VersionedSchema()
                .register(1, Arrays.asList(
                        new FieldDefinition("candidateName", FieldType.STRING, true, 1),
                        new FieldDefinition("score", FieldType.INTEGER, true, 1)))
                .register(2, Arrays.asList(
                        new FieldDefinition("candidateName", FieldType.STRING, true, 2),
                        new FieldDefinition("evaluationScore", FieldType.INTEGER, true, 2),
                        new FieldDefinition("remote", FieldType.BOOLEAN, true, 2)))
                .addMigration(new FieldMigrationRule("score", "evaluationScore", null))
                .addMigration(new FieldMigrationRule("remote", "remote", false));
        DynamicRecord oldRecord = new DynamicRecord().put("candidateName", "小羊").put("score", 95);

        DynamicRecord migrated = schemas.migrate(oldRecord, 1, 2);

        assertEquals(0, schemas.validate(2, migrated).size());
        assertEquals(95, migrated.get("evaluationScore"));
        assertEquals(false, migrated.get("remote"));
    }

    private static WorkflowDefinition workflow() {
        return new WorkflowDefinition("PENDING_INTERVIEW", Arrays.asList(
                new WorkflowTransition("PENDING_INTERVIEW", "startInterview", "IN_INTERVIEW"),
                new WorkflowTransition("IN_INTERVIEW", "submitEvaluation", "COMPLETED"),
                new WorkflowTransition("PENDING_INTERVIEW", "cancel", "CANCELED")));
    }
}
