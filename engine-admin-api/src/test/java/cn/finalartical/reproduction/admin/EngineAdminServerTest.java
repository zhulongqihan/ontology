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
            HttpResponse rollback = request(server, "POST", "/api/runs/" + run.getId() + "/rollback", "{}");
            assertEquals(200, detail.status);
            assertEquals(200, trace.status);
            assertEquals(200, snapshots.status);
            assertEquals(200, idempotency.status);
            assertEquals(200, export.status);
            assertEquals(200, rollback.status);
            assertTrue(detail.body.contains("beforeSnapshot"));
            assertTrue(trace.body.contains("validation"));
            assertTrue(snapshots.body.contains("BEFORE"));
            assertTrue(idempotency.body.contains("http-1"));
            assertTrue(export.body.contains("ENGINE_RUNTIME_RESULT"));
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

    private HttpResponse request(EngineAdminServer server, String method, String path, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + server.getPort() + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = connection.getResponseCode();
        InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        return new HttpResponse(status, read(input));
    }

    private String read(InputStream input) throws Exception {
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

        private HttpResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
