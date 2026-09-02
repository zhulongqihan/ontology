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
            assertEquals(200, detail.status);
            assertEquals(200, trace.status);
            assertTrue(detail.body.contains("beforeSnapshot"));
            assertTrue(trace.body.contains("validation"));
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
