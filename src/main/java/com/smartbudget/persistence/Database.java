package com.smartbudget.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the single SQLite connection and applies the schema on first use.
 *
 * <p>This is the one Singleton in the project, and it exists for a concrete
 * reason rather than as a pattern demonstration: SQLite is a single file, the
 * {@code PRAGMA foreign_keys} setting is per-connection, and an in-memory test
 * database only survives as long as its connection is held. Scattering
 * {@code DriverManager.getConnection} calls through the DAOs would silently
 * disable foreign keys on some of them.
 *
 * <p>The singleton is not forced on the DAOs: they receive a {@code Database}
 * through their constructor, so tests inject {@link #inMemory()} and never touch
 * {@link #getInstance()}. That keeps the convenience of a global instance in the
 * application without making the persistence layer untestable.
 */
public final class Database {

    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String DEFAULT_FILE = "smartbudget.db";

    private static Database instance;

    private final Connection connection;

    private Database(String jdbcUrl) {
        try {
            this.connection = DriverManager.getConnection(jdbcUrl);
        } catch (SQLException e) {
            throw new DataAccessException("Could not open database: " + jdbcUrl, e);
        }
        enableForeignKeys();
        migrate();
    }

    /** The application-wide, file-backed database. */
    public static synchronized Database getInstance() {
        if (instance == null) {
            instance = new Database("jdbc:sqlite:" + DEFAULT_FILE);
        }
        return instance;
    }

    /**
     * A fresh, isolated, in-memory database with the schema and seed data
     * applied. Each call returns an independent database, so tests cannot leak
     * state into one another.
     */
    public static Database inMemory() {
        return new Database("jdbc:sqlite::memory:");
    }

    /**
     * Opens (or creates) a database at an explicit path. Used by tests that need
     * to prove data survives a close-and-reopen cycle, which an in-memory
     * database cannot demonstrate.
     */
    public static Database atPath(String path) {
        return new Database("jdbc:sqlite:" + path);
    }

    public Connection getConnection() {
        return connection;
    }

    private void enableForeignKeys() {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            throw new DataAccessException("Could not enable foreign key enforcement", e);
        }
    }

    /**
     * Creates the schema, and seeds it only when the database is brand new.
     * Re-running on an existing database is a no-op, so user data is never
     * overwritten by the seeder on the second launch.
     */
    private void migrate() {
        execute(readResource("/db/schema.sql"));

        if (appliedVersion() >= CURRENT_SCHEMA_VERSION) {
            return;
        }
        execute(readResource("/db/seed.sql"));
        execute("INSERT INTO schema_version (version) VALUES (" + CURRENT_SCHEMA_VERSION + ")");
    }

    private int appliedVersion() {
        String sql = "SELECT COALESCE(MAX(version), 0) FROM schema_version";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DataAccessException("Could not read schema version", e);
        }
    }

    private void execute(String sql) {
        try (Statement statement = connection.createStatement()) {
            for (String part : splitStatements(sql)) {
                statement.execute(part);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to execute SQL script", e);
        }
    }

    /**
     * Splits a script on semicolons that end a statement.
     *
     * <p>The seed script contains semicolon-free multi-line statements and SQL
     * line comments, so comments are stripped first; splitting naively on every
     * {@code ;} would break on a semicolon inside a comment.
     */
    private static String[] splitStatements(String script) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : script.split("\\R")) {
            int comment = line.indexOf("--");
            cleaned.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }
        return java.util.Arrays.stream(cleaned.toString().split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    private static String readResource(String path) {
        try (InputStream in = Database.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new DataAccessException("Missing SQL resource on classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataAccessException("Could not read SQL resource: " + path, e);
        }
    }

    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new DataAccessException("Could not close database", e);
        }
    }
}
