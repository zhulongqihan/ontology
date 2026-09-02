package cn.finalartical.reproduction.admin;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.ConcurrentModificationException;

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
        if (state == null) {
            throw new IllegalArgumentException("engine state must not be null");
        }
        long expectedRevision = readRevision();
        write(state, expectedRevision);
    }

    @Override
    public synchronized void save(EngineState state, long expectedRevision) {
        if (state == null) {
            throw new IllegalArgumentException("engine state must not be null");
        }
        long actualRevision = readRevision();
        if (actualRevision != expectedRevision) {
            throw new ConcurrentModificationException("engine state revision conflict: expected "
                    + expectedRevision + " but was " + actualRevision);
        }
        write(state, expectedRevision);
    }

    @Override
    public synchronized void markPersistenceCommitted(EngineState state, String runId) {
        if (state == null) {
            throw new IllegalArgumentException("engine state must not be null");
        }
        long actualRevision = readRevision();
        if (actualRevision != state.getRevision()) {
            throw new ConcurrentModificationException("engine state revision conflict while marking trace: expected "
                    + state.getRevision() + " but was " + actualRevision);
        }
        TraceLifecycle.markPersistenceCommitted(state, runId);
        writeAtomic(state);
    }

    private void write(EngineState state, long expectedRevision) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            state.setRevision(expectedRevision + 1L);
            writeAtomic(state);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot save engine state: " + path, exception);
        }
    }

    private void writeAtomic(EngineState state) {
        try {
            byte[] json = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(state)
                    .getBytes(StandardCharsets.UTF_8);
            Path temporary = path.resolveSibling(path.getFileName().toString() + ".tmp");
            Files.write(temporary, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("cannot save engine state: " + path, exception);
        }
    }

    private long readRevision() {
        if (!Files.exists(path)) {
            return 0L;
        }
        try {
            return mapper.readValue(Files.readAllBytes(path), EngineState.class).getRevision();
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read engine state revision: " + path, exception);
        }
    }

    public Path getPath() {
        return path;
    }
}
