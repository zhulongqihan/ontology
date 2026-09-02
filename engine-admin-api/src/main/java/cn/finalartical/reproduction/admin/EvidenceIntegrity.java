package cn.finalartical.reproduction.admin;

import cn.finalartical.reproduction.flexible.ExecutionSnapshot;
import cn.finalartical.reproduction.flexible.ExecutionStatus;

import java.time.Instant;
import java.util.Map;

/** Validates persisted evidence instead of trusting its serialized fields. */
public final class EvidenceIntegrity {
    private EvidenceIntegrity() {
    }

    public static void validateState(EngineState state) {
        if (state == null || state.getRuns() == null) {
            throw new IllegalStateException("engine state runs must not be null");
        }
        for (RuntimeRun run : state.getRuns()) {
            if (run == null || "REPRODUCED_SYSTEM_RUN".equals(run.getDataIdentity())
                    || EngineAdminService.LEGACY_RUNTIME_IDENTITY.equals(run.getDataIdentity())
                    || "LEGACY_RUNTIME_RECORD".equals(run.getDataIdentity())) {
                continue;
            }
            if (!EngineAdminService.DATA_IDENTITY.equals(run.getDataIdentity())) {
                throw new IllegalStateException("unsupported runtime data identity: " + run.getId());
            }
            if (run.getId() == null || run.getModelId() == null || run.getContextId() == null
                    || run.getTrace() == null || run.getBeforeSnapshot() == null || run.getAfterSnapshot() == null) {
                throw new IllegalStateException("runtime evidence is incomplete: " + run.getId());
            }
            validateSnapshot(run.getBeforeSnapshot(), run.getId(), "BEFORE", run);
            validateSnapshot(run.getAfterSnapshot(), run.getId(), "AFTER", run);
            validateTrace(run);
            if (run.getOntologyTypeId() != null) {
                if (run.getOntologyVersion() < 1 || isBlank(run.getOntologyDefinitionSha256())) {
                    throw new IllegalStateException("runtime ontology binding is incomplete: " + run.getId());
                }
                OntologyTypeConfig type = findOntologyType(state, run.getOntologyTypeId());
                if (type == null) {
                    throw new IllegalStateException("runtime ontology type is missing: " + run.getOntologyTypeId());
                }
                if (type.getVersion() != run.getOntologyVersion()
                        || !OntologyDefinitionHasher.sha256(type).equals(run.getOntologyDefinitionSha256())) {
                    throw new IllegalStateException("runtime ontology definition no longer matches run: " + run.getId());
                }
            }
        }
    }

    public static void validateSnapshot(ExecutionSnapshotRecord record, String runId) {
        validateSnapshot(record, runId, record == null ? null : record.getPhase(), null);
    }

    private static void validateSnapshot(ExecutionSnapshotRecord record, String runId,
                                         String expectedPhase, RuntimeRun run) {
        if (record == null || isBlank(record.getContextId()) || isBlank(record.getModelId())
                || isBlank(record.getPhase()) || isBlank(record.getState()) || isBlank(record.getCapturedAt())
                || isBlank(record.getSha256()) || record.getValues() == null
                || (expectedPhase != null && !expectedPhase.equals(record.getPhase()))
                || (run != null && (!run.getContextId().equals(record.getContextId())
                || !run.getModelId().equals(record.getModelId())))) {
            throw new IllegalStateException("snapshot is incomplete: " + runId);
        }
        try {
            Instant.parse(record.getCapturedAt());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("snapshot timestamp is invalid: " + runId, exception);
        }
        if (record.getSchemaVersion() < 1 || record.getWorkflowVersion() < 1) {
            throw new IllegalStateException("snapshot version is invalid: " + runId);
        }
        ExecutionStatus status;
        try {
            status = ExecutionStatus.valueOf(record.getStatus());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("snapshot status is invalid: " + runId, exception);
        }
        ExecutionSnapshot expected = new ExecutionSnapshot(record.getContextId(), record.getModelId(),
                record.getSchemaVersion(), record.getWorkflowVersion(), record.getState(), status,
                record.getCapturedAt(), record.getValues());
        if (!expected.getSha256().equals(record.getSha256())) {
            throw new IllegalStateException("snapshot sha256 mismatch: " + runId + ":" + record.getPhase());
        }
    }

    public static void validateTrace(RuntimeRun run) {
        TraceRecord trace = run.getTrace();
        if (!run.getId().equals(trace.getRunId()) || !run.getTraceId().equals(trace.getTraceId())
                || isBlank(trace.getStartedAt()) || isBlank(trace.getEndedAt()) || trace.getDurationMs() < 0
                || isBlank(trace.getStatus()) || isBlank(trace.getLifecycle())
                || !isAllowedLifecycle(trace.getLifecycle()) || !trace.isSealed()) {
            throw new IllegalStateException("trace identity or lifecycle is invalid: " + run.getId());
        }
        try {
            Instant.parse(trace.getStartedAt());
            Instant.parse(trace.getEndedAt());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("trace timestamp is invalid: " + run.getId(), exception);
        }
        boolean persistenceSeen = false;
        for (TraceSpanRecord span : trace.getSpans()) {
            if (span == null || isBlank(span.getSpanId()) || !trace.getTraceId().equals(span.getTraceId())
                    || isBlank(span.getStartedAt()) || isBlank(span.getEndedAt()) || span.getDurationMs() < 0
                    || isBlank(span.getName()) || isBlank(span.getStatus()) || span.getAttributes() == null) {
                throw new IllegalStateException("trace span is invalid: " + run.getId());
            }
            try {
                Instant.parse(span.getStartedAt());
                Instant.parse(span.getEndedAt());
            } catch (RuntimeException exception) {
                throw new IllegalStateException("trace span timestamp is invalid: " + run.getId(), exception);
            }
            if ("persistence".equals(span.getName())) {
                persistenceSeen = true;
                if ("COMMITTED".equals(trace.getLifecycle()) && !"COMMITTED".equals(span.getStatus())) {
                    throw new IllegalStateException("committed trace has an uncommitted persistence span: " + run.getId());
                }
                if ("COMMITTED".equals(trace.getLifecycle())
                        && !"repository.commit".equals(span.getAttributes().get("commitBoundary"))) {
                    throw new IllegalStateException("committed trace has no repository commit boundary: " + run.getId());
                }
            }
        }
        if (!persistenceSeen) {
            throw new IllegalStateException("trace has no persistence span: " + run.getId());
        }
    }

    private static OntologyTypeConfig findOntologyType(EngineState state, String id) {
        for (OntologyTypeConfig type : state.getOntologyTypes()) {
            if (type != null && id.equals(type.getId())) {
                return type;
            }
        }
        return null;
    }

    private static boolean isAllowedLifecycle(String lifecycle) {
        return "PREPARED".equals(lifecycle) || "COMMITTED".equals(lifecycle);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
