package cn.finalartical.reproduction.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ConcurrentModificationException;
import java.util.concurrent.Executors;

public final class EngineAdminServer {
    private final HttpServer server;
    private final EngineAdminService service;
    private final ObjectMapper mapper;

    private EngineAdminServer(HttpServer server, EngineAdminService service) {
        this.server = server;
        this.service = service;
        this.mapper = new ObjectMapper();
    }

    public static EngineAdminServer start(int port, Path statePath) throws IOException {
        return start(port, new JsonEngineStateRepository(statePath));
    }

    public static EngineAdminServer start(int port, EngineStateRepository repository) throws IOException {
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        EngineAdminService service = new EngineAdminService(repository);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        EngineAdminServer adminServer = new EngineAdminServer(server, service);
        server.createContext("/api", adminServer.new ApiHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        return adminServer;
    }

    public EngineAdminService getService() {
        return service;
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
    }

    private final class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            try {
                dispatch(exchange);
            } catch (JsonProcessingException exception) {
                writeError(exchange, 400, "invalid JSON payload: " + exception.getOriginalMessage());
            } catch (IllegalArgumentException exception) {
                writeError(exchange, 400, exception.getMessage());
            } catch (ConcurrentModificationException exception) {
                writeError(exchange, 409, exception.getMessage());
            } catch (Exception exception) {
                writeError(exchange, 500, exception.getMessage() == null ? "internal server error" : exception.getMessage());
            }
        }
    }

    private void dispatch(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String relative = path.substring("/api".length());
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        List<String> segments = relative.isEmpty()
                ? Collections.<String>emptyList()
                : Arrays.asList(relative.split("/"));
        String method = exchange.getRequestMethod().toUpperCase();

        if (segments.size() == 1 && "health".equals(segments.get(0)) && "GET".equals(method)) {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("status", "UP");
            result.put("engine", "flexible-engine-ontology");
            result.put("dataIdentity", EngineAdminService.DATA_IDENTITY);
            writeJson(exchange, 200, result);
            return;
        }
        if (segments.size() == 1 && "overview".equals(segments.get(0)) && "GET".equals(method)) {
            writeJson(exchange, 200, service.overview());
            return;
        }
        if (segments.size() == 1 && "models".equals(segments.get(0)) && "GET".equals(method)) {
            writeJson(exchange, 200, service.models());
            return;
        }
        if (segments.size() == 1 && "models".equals(segments.get(0)) && "POST".equals(method)) {
            writeJson(exchange, 201, service.addModel(readPayload(exchange)));
            return;
        }
        if (segments.size() == 2 && "models".equals(segments.get(0)) && "GET".equals(method)) {
            writeJson(exchange, 200, service.model(decode(segments.get(1))));
            return;
        }
        if (segments.size() == 3 && "models".equals(segments.get(0)) && "fields".equals(segments.get(2)) && "POST".equals(method)) {
            writeJson(exchange, 201, service.addField(decode(segments.get(1)), readPayload(exchange)));
            return;
        }
        if (segments.size() == 3 && "models".equals(segments.get(0)) && "transitions".equals(segments.get(2)) && "POST".equals(method)) {
            writeJson(exchange, 201, service.addTransition(decode(segments.get(1)), readPayload(exchange)));
            return;
        }
        if (segments.size() == 2 && "ontology".equals(segments.get(0)) && "types".equals(segments.get(1)) && "GET".equals(method)) {
            writeJson(exchange, 200, service.ontologyTypes());
            return;
        }
        if (segments.size() == 2 && "ontology".equals(segments.get(0)) && "types".equals(segments.get(1)) && "POST".equals(method)) {
            writeJson(exchange, 201, service.addOntologyType(readPayload(exchange)));
            return;
        }
        if (segments.size() == 4 && "ontology".equals(segments.get(0)) && "types".equals(segments.get(1))
                && "relations".equals(segments.get(3)) && "POST".equals(method)) {
            writeJson(exchange, 201, service.addOntologyRelation(decode(segments.get(2)), readPayload(exchange)));
            return;
        }
        if (segments.size() == 1 && "services".equals(segments.get(0)) && "GET".equals(method)) {
            writeJson(exchange, 200, service.services());
            return;
        }
        if (segments.size() == 1 && "services".equals(segments.get(0)) && "POST".equals(method)) {
            writeJson(exchange, 201, service.addService(readPayload(exchange)));
            return;
        }
        if (segments.size() == 1 && "runs".equals(segments.get(0)) && "GET".equals(method)) {
            writeJson(exchange, 200, service.runs());
            return;
        }
        if (segments.size() == 3 && "runs".equals(segments.get(0)) && "retry".equals(segments.get(2))
                && "POST".equals(method)) {
            writeJson(exchange, 200, service.retry(decode(segments.get(1))));
            return;
        }
        if (segments.size() == 3 && "runs".equals(segments.get(0)) && "rollback".equals(segments.get(2))
                && "POST".equals(method)) {
            writeJson(exchange, 200, service.rollback(decode(segments.get(1))));
            return;
        }
        if (segments.size() == 2 && "runs".equals(segments.get(0)) && "GET".equals(method)) {
            writeJson(exchange, 200, service.run(decode(segments.get(1))));
            return;
        }
        if (segments.size() == 3 && "runs".equals(segments.get(0)) && "trace".equals(segments.get(2))
                && "GET".equals(method)) {
            writeJson(exchange, 200, service.run(decode(segments.get(1))).getTrace());
            return;
        }
        if (segments.size() == 3 && "runs".equals(segments.get(0)) && "snapshots".equals(segments.get(2))
                && "GET".equals(method)) {
            writeJson(exchange, 200, service.snapshots(decode(segments.get(1))));
            return;
        }
        if (segments.size() == 2 && "contexts".equals(segments.get(0)) && "GET".equals(method)) {
            writeJson(exchange, 200, service.context(decode(segments.get(1))));
            return;
        }
        if (segments.size() == 1 && "contexts".equals(segments.get(0)) && "GET".equals(method)) {
            writeJson(exchange, 200, service.contexts());
            return;
        }
        if (segments.size() == 1 && "audit-events".equals(segments.get(0)) && "GET".equals(method)) {
            writeJson(exchange, 200, service.auditEvents());
            return;
        }
        if (segments.size() == 1 && "idempotency-records".equals(segments.get(0)) && "GET".equals(method)) {
            writeJson(exchange, 200, service.idempotencyRecords());
            return;
        }
        if (segments.size() == 1 && "export".equals(segments.get(0)) && "GET".equals(method)) {
            writeJson(exchange, 200, service.exportState());
            return;
        }
        if (segments.size() == 2 && "runtime".equals(segments.get(0)) && "execute".equals(segments.get(1)) && "POST".equals(method)) {
            writeJson(exchange, 200, service.execute(readPayload(exchange)));
            return;
        }
        writeError(exchange, 404, "route not found: " + method + " " + path);
    }

    private Map<String, Object> readPayload(HttpExchange exchange) throws IOException {
        String body = readBody(exchange.getRequestBody());
        if (body.trim().isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        return mapper.readValue(body, new TypeReference<Map<String, Object>>() { });
    }

    private String readBody(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int length;
        while ((length = input.read(chunk)) != -1) {
            buffer.write(chunk, 0, length);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private void writeError(HttpExchange exchange, int status, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("error", message == null ? "unknown error" : message);
        writeJson(exchange, status, body);
    }

    private void writeJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = mapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
    }

    private String decode(String value) throws IOException {
        return URLDecoder.decode(value, "UTF-8");
    }
}
