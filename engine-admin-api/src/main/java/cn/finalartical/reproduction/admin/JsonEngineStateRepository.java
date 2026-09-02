package cn.finalartical.reproduction.admin;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class JsonEngineStateRepository implements EngineStateRepository {
    private final Path path;
    private final ObjectMapper mapper;

    public JsonEngineStateRepository(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("state path must not be null");
        }
        this.path = path;
        this.mapper = new ObjectMapper();
    }

    @Override
    public synchronized EngineState load() {
        if (!Files.exists(path)) {
            EngineState seed = DefaultEngineSeed.create();
            save(seed);
            return seed;
        }
        try {
            return mapper.readValue(Files.readAllBytes(path), EngineState.class);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot load engine state: " + path, exception);
        }
    }

    @Override
    public synchronized void save(EngineState state) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            byte[] json = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(state)
                    .getBytes(StandardCharsets.UTF_8);
            Files.write(path, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot save engine state: " + path, exception);
        }
    }

    public Path getPath() {
        return path;
    }
}
