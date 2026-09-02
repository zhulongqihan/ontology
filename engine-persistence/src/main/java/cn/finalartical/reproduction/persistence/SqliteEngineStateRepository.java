package cn.finalartical.reproduction.persistence;

import cn.finalartical.reproduction.admin.DefaultEngineSeed;
import cn.finalartical.reproduction.admin.EngineState;
import cn.finalartical.reproduction.admin.EngineStateRepository;
import cn.finalartical.reproduction.admin.JsonEngineStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

public final class SqliteEngineStateRepository implements EngineStateRepository {
    private static final int SCHEMA_VERSION = 1;

    private final Path databasePath;
    private final Path legacyJsonPath;
    private final ObjectMapper mapper = new ObjectMapper();

    public SqliteEngineStateRepository(Path databasePath) {
        this(databasePath, null);
    }

    public SqliteEngineStateRepository(Path databasePath, Path legacyJsonPath) {
        if (databasePath == null) {
            throw new IllegalArgumentException("database path must not be null");
        }
        this.databasePath = databasePath;
        this.legacyJsonPath = legacyJsonPath;
    }

    @Override
    public synchronized EngineState load() {
        try (Connection connection = open()) {
            migrate(connection);
            String payload = readPayload(connection);
            if (payload == null) {
                EngineState seed = loadSeed();
                write(connection, seed);
                return seed;
            }
            return mapper.readValue(payload, EngineState.class);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot decode SQLite engine state: " + databasePath, exception);
        } catch (SQLException exception) {
            throw new IllegalStateException("cannot load SQLite engine state: " + databasePath, exception);
        }
    }

    @Override
    public synchronized void save(EngineState state) {
        if (state == null) {
            throw new IllegalArgumentException("engine state must not be null");
        }
        try (Connection connection = open()) {
            migrate(connection);
            write(connection, state);
        } catch (SQLException exception) {
            throw new IllegalStateException("cannot save SQLite engine state: " + databasePath, exception);
        }
    }

    public Path getDatabasePath() {
        return databasePath;
    }

    public void backupTo(Path target) {
        if (target == null) {
            throw new IllegalArgumentException("backup target must not be null");
        }
        load();
        try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(databasePath, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot backup SQLite engine state: " + target, exception);
        }
    }

    private Connection open() throws SQLException {
        Path parent = databasePath.toAbsolutePath().getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("cannot create database directory: " + parent, exception);
        }
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
        connection.createStatement().execute("PRAGMA foreign_keys = ON");
        return connection;
    }

    private void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL PRIMARY KEY, applied_at TEXT NOT NULL)");
            if (!hasVersion(connection, SCHEMA_VERSION)) {
                for (String sql : readMigration()) {
                    if (!sql.trim().isEmpty()) {
                        statement.execute(sql);
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO schema_version(version, applied_at) VALUES (?, ?)")) {
                    insert.setInt(1, SCHEMA_VERSION);
                    insert.setString(2, Instant.now().toString());
                    insert.executeUpdate();
                }
            }
        }
    }

    private boolean hasVersion(Connection connection, int version) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT version FROM schema_version WHERE version = ?")) {
            query.setInt(1, version);
            try (ResultSet result = query.executeQuery()) {
                return result.next();
            }
        }
    }

    private String[] readMigration() {
        try (InputStream input = SqliteEngineStateRepository.class.getResourceAsStream(
                "/schema/001_initial.sql")) {
            if (input == null) {
                throw new IllegalStateException("missing SQLite migration resource");
            }
            String sql = new String(readAll(input), StandardCharsets.UTF_8);
            return sql.split(";\\s*(?:\\r?\\n|$)");
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read SQLite migration", exception);
        }
    }

    private String readPayload(Connection connection) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT payload_json FROM engine_state WHERE state_id = 1")) {
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    private void write(Connection connection, EngineState state) throws SQLException {
        String payload;
        try {
            payload = mapper.writeValueAsString(state);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot encode SQLite engine state", exception);
        }
        String hash = sha256(payload);
        connection.setAutoCommit(false);
        try {
            long revision = currentRevision(connection) + 1L;
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE engine_state SET payload_json = ?, payload_sha256 = ?, revision = ?, updated_at = ? WHERE state_id = 1")) {
                update.setString(1, payload);
                update.setString(2, hash);
                update.setLong(3, revision);
                update.setString(4, state.getUpdatedAt() == null ? Instant.now().toString() : state.getUpdatedAt());
                int updated = update.executeUpdate();
                if (updated == 0) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO engine_state(state_id, payload_json, payload_sha256, revision, updated_at) VALUES (1, ?, ?, ?, ?)")) {
                        insert.setString(1, payload);
                        insert.setString(2, hash);
                        insert.setLong(3, revision);
                        insert.setString(4, state.getUpdatedAt() == null ? Instant.now().toString() : state.getUpdatedAt());
                        insert.executeUpdate();
                    }
                }
            }
            try (PreparedStatement audit = connection.prepareStatement(
                    "INSERT INTO state_write(revision, payload_sha256, written_at) VALUES (?, ?, ?)")) {
                audit.setLong(1, revision);
                audit.setString(2, hash);
                audit.setString(3, Instant.now().toString());
                audit.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private long currentRevision(Connection connection) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT revision FROM engine_state WHERE state_id = 1")) {
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }
    }

    private EngineState loadSeed() {
        if (legacyJsonPath != null && Files.exists(legacyJsonPath)) {
            return new JsonEngineStateRepository(legacyJsonPath).load();
        }
        return DefaultEngineSeed.create();
    }

    private static byte[] readAll(InputStream input) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length;
        while ((length = input.read(buffer)) != -1) {
            output.write(buffer, 0, length);
        }
        return output.toByteArray();
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
