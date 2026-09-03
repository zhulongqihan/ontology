package cn.finalartical.reproduction.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EngineAdminServerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void httpExecuteReturnsQueryableRunSnapshotsAndTrace() throws Exception {
        Path path = Files.createTempDirectory("engine-http").resolve("state.json");
        EngineAdminServer server = EngineAdminServer.start(0, path);
        try {
            HttpResponse health = request(server, "GET", "/api/health", null);
            assertEquals(200, health.status);
            assertTrue(health.body.contains("ENGINE_RUNTIME_RESULT"));

            Map<String, Object> values = new LinkedHashMap<String, Object>();
            values.put("candidateName", "HTTP 链路");
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("modelId", "interview-session");
            payload.put("contextId", "ctx-http");
            payload.put("event", "startInterview");
            payload.put("idempotencyKey", "http-1");
            payload.put("values", values);
            HttpResponse execute = request(server, "POST", "/api/runtime/execute", mapper.writeValueAsString(payload));
            assertEquals(200, execute.status);
            RuntimeRun run = mapper.readValue(execute.body, RuntimeRun.class);

            HttpResponse detail = request(server, "GET", "/api/runs/" + run.getId(), null);
            HttpResponse trace = request(server, "GET", "/api/runs/" + run.getId() + "/trace", null);
            HttpResponse snapshots = request(server, "GET", "/api/runs/" + run.getId() + "/snapshots", null);
            HttpResponse idempotency = request(server, "GET", "/api/idempotency-records", null);
            HttpResponse export = request(server, "GET", "/api/export", null);
            HttpResponse replay = request(server, "POST", "/api/runs/" + run.getId() + "/replay", "{}");
            HttpResponse rollback = request(server, "POST", "/api/runs/" + run.getId() + "/rollback", "{}");
            assertEquals(200, detail.status);
            assertEquals(200, trace.status);
            assertEquals(200, snapshots.status);
            assertEquals(200, idempotency.status);
            assertEquals(200, export.status);
            assertEquals(200, replay.status);
            assertEquals(200, rollback.status);
            assertTrue(detail.body.contains("beforeSnapshot"));
            assertTrue(trace.body.contains("validation"));
            assertTrue(snapshots.body.contains("BEFORE"));
            assertTrue(idempotency.body.contains("http-1"));
            assertTrue(export.body.contains("ENGINE_RUNTIME_RESULT"));
            assertTrue(replay.body.contains("replayOfRunId"));
            assertTrue(rollback.body.contains("ROLLED_BACK"));
        } finally {
            server.stop();
        }
    }

    @Test
    public void httpPublishesSchemaRenameAndRemovalAsNewVersions() throws Exception {
        Path path = Files.createTempDirectory("engine-http-schema").resolve("state.json");
        EngineAdminServer server = EngineAdminServer.start(0, path);
        try {
            Map<String, Object> rename = new LinkedHashMap<String, Object>();
            rename.put("sourceName", "candidateName");
            rename.put("targetName", "applicantName");
            HttpResponse renamed = request(server, "POST", "/api/models/interview-session/fields/rename",
                    mapper.writeValueAsString(rename));
            assertEquals(201, renamed.status);
            assertTrue(renamed.body.contains("applicantName"));

            Map<String, Object> removal = new LinkedHashMap<String, Object>();
            removal.put("name", "score");
            HttpResponse removed = request(server, "POST", "/api/models/interview-session/fields/remove",
                    mapper.writeValueAsString(removal));
            assertEquals(200, removed.status);
            assertTrue(removed.body.contains("schemaVersion"));

            HttpResponse model = request(server, "GET", "/api/models/interview-session", null);
            assertEquals(200, model.status);
            assertTrue(model.body.contains("schemaMigrations"));
            assertTrue(model.body.contains("applicantName"));
        } finally {
            server.stop();
        }
    }

    @Test
    public void httpExposesExplicitModelOntologyBinding() throws Exception {
        Path path = Files.createTempDirectory("engine-http-binding").resolve("state.json");
        EngineAdminServer server = EngineAdminServer.start(0, path);
        try {
            HttpResponse binding = request(server, "PUT", "/api/models/interview-session/ontology-binding",
                    "{\"ontologyTypeId\":\"questionnaire\"}");
            assertEquals(200, binding.status);
            assertTrue(binding.body.contains("ontologyTypeId"));
            assertTrue(binding.body.contains("questionnaire"));
        } finally {
            server.stop();
        }
    }

    @Test
    public void httpExecutesAndReturnsAFormalBaselineFlexiblePair() throws Exception {
        Path path = Files.createTempDirectory("engine-http-comparison").resolve("state.json");
        EngineAdminServer server = EngineAdminServer.start(0, path);
        try {
            Map<String, Object> values = new LinkedHashMap<String, Object>();
            values.put("name", "HTTP 对比");
            values.put("subjectId", "s-http");
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("comparisonId", "cmp-http-001");
            payload.put("caseId", "questionnaire-basic");
            payload.put("modelId", "questionnaire");
            payload.put("event", "publish");
            payload.put("values", values);

            HttpResponse response = request(server, "POST", "/api/comparisons/execute",
                    mapper.writeValueAsString(payload));
            assertEquals(200, response.status);
            assertTrue(response.body.contains("RIGID_MAPPING_BASELINE"));
            assertTrue(response.body.contains("FLEXIBLE_ENGINE"));
            assertTrue(response.body.contains("cmp-http-001"));
            assertTrue(response.body.contains("pairedRunId"));
            assertTrue(response.body.contains("inputSha256"));

            HttpResponse history = request(server, "GET", "/api/comparisons", null);
            HttpResponse detail = request(server, "GET", "/api/comparisons/cmp-http-001", null);
            assertEquals(200, history.status);
            assertEquals(200, detail.status);
            assertTrue(history.body.contains("COMPLETE"));
            assertTrue(history.body.contains("baselineRunId"));
            assertTrue(!history.body.contains("beforeSnapshot"));
            assertTrue(detail.body.contains("beforeSnapshot"));
            assertTrue(detail.body.contains("evidenceComplete"));

            values.put("name", "HTTP 对比冲突");
            HttpResponse conflict = request(server, "POST", "/api/comparisons/execute",
                    mapper.writeValueAsString(payload));
            assertEquals(400, conflict.status);
            assertTrue(conflict.body.contains("different request"));
        } finally {
            server.stop();
        }
    }

    @Test
    public void httpConfigurationUpdatesAndErrorsHaveStableContracts() throws Exception {
        Path path = Files.createTempDirectory("engine-http-contract").resolve("state.json");
        EngineAdminServer server = EngineAdminServer.start(0, path);
        try {
            HttpResponse relation = request(server, "PUT", "/api/ontology/types/questionnaire/relations/containsSubject",
                    "{\"cardinality\":\"1:1\"}");
            HttpResponse provider = request(server, "PUT", "/api/services/ontology-assembler",
                    "{\"status\":\"DOWN\"}");
            HttpResponse malformed = requestWithTrace(server, "POST", "/api/runtime/execute", "{", "review-trace-001");
            HttpResponse missing = request(server, "GET", "/api/does-not-exist", null);

            assertEquals(200, relation.status);
            assertTrue(relation.body.contains("1:1"));
            assertEquals(200, provider.status);
            assertTrue(provider.body.contains("DOWN"));
            assertEquals(400, malformed.status);
            assertTrue(malformed.body.contains("INVALID_JSON"));
            assertTrue(malformed.body.contains("review-trace-001"));
            assertEquals(404, missing.status);
            assertTrue(missing.body.contains("ROUTE_NOT_FOUND"));
            assertTrue(missing.body.contains("traceId"));

            String currentRevision = String.valueOf(server.getService().revision());
            HttpResponse stale = requestWithHeader(server, "PUT", "/api/services/ontology-assembler",
                    "{\"status\":\"READY\"}", "If-Match", "\"" + (Long.parseLong(currentRevision) - 1) + "\"");
            assertEquals(409, stale.status);
            assertTrue(stale.body.contains("REVISION_CONFLICT"));
        } finally {
            server.stop();
        }
    }

    @Test
    public void corsPreflightAdvertisesConditionalWriteAndEvidenceHeaders() throws Exception {
        Path path = Files.createTempDirectory("engine-http-cors").resolve("state.json");
        EngineAdminServer server = EngineAdminServer.start(0, path);
        try {
            HttpResponse preflight = requestWithHeader(server, "OPTIONS", "/api/models", null,
                    "Access-Control-Request-Headers", "Content-Type, If-Match");
            assertEquals(204, preflight.status);
            assertTrue(preflight.allowHeaders.contains("If-Match"));
            assertTrue(preflight.exposeHeaders.contains("ETag"));
        } finally {
            server.stop();
        }
    }

    private HttpResponse request(EngineAdminServer server, String method, String path, String body) throws Exception {
        return requestWithTrace(server, method, path, body, null);
    }

    private HttpResponse requestWithTrace(EngineAdminServer server, String method, String path, String body,
                                          String traceId) throws Exception {
        return requestWithHeader(server, method, path, body, traceId == null ? null : "X-Trace-Id", traceId);
    }

    private HttpResponse requestWithHeader(EngineAdminServer server, String method, String path, String body,
                                           String header, String value) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + server.getPort() + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        if (header != null && value != null) {
            connection.setRequestProperty(header, value);
        }
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = connection.getResponseCode();
        InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        return new HttpResponse(status, read(input), connection.getHeaderField("Access-Control-Allow-Headers"),
                connection.getHeaderField("Access-Control-Expose-Headers"));
    }

    private String read(InputStream input) throws Exception {
        if (input == null) {
            return "";
        }
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = source.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static final class HttpResponse {
        private final int status;
        private final String body;
        private final String allowHeaders;
        private final String exposeHeaders;

        private HttpResponse(int status, String body, String allowHeaders, String exposeHeaders) {
            this.status = status;
            this.body = body;
            this.allowHeaders = allowHeaders == null ? "" : allowHeaders;
            this.exposeHeaders = exposeHeaders == null ? "" : exposeHeaders;
        }
    }
}
