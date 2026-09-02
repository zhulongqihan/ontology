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
