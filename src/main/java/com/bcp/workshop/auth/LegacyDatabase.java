package com.bcp.workshop.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.runtime.Startup;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@ApplicationScoped
@Startup
public class LegacyDatabase {
    private static final String DB_URL = System.getenv().getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");
    private static final String SQLITE_PREFIX = "jdbc:sqlite:";
    private static final String FIND_BY_USERNAME_SQL =
            "SELECT id, username, password_hash, display_name, role FROM legacy_users WHERE username = ?";
    private final PasswordHasher hasher;
    private final PasswordMigration migration;

    public LegacyDatabase() {
        this(new PasswordHasher(), new PasswordMigration());
    }

    @Inject
    public LegacyDatabase(PasswordHasher hasher, PasswordMigration migration) {
        this.hasher = hasher;
        this.migration = migration;
    }

    @PostConstruct
    void initialize() {
        try {
            migration.migrate(databasePath());
            try (Connection connection = DriverManager.getConnection(DB_URL)) {
                seedWorkshopUsers(connection);
            }
        } catch (Exception error) {
            throw new IllegalStateException("Authentication database initialization failed", error);
        }
    }

    private Path databasePath() {
        if (!DB_URL.startsWith(SQLITE_PREFIX) || DB_URL.contains("?") || DB_URL.contains(":memory:")
                || DB_URL.startsWith(SQLITE_PREFIX + "file:")) {
            throw new IllegalStateException("Authentication migration requires a local SQLite file URL");
        }
        return Path.of(DB_URL.substring(SQLITE_PREFIX.length()));
    }

    private void seedWorkshopUsers(Connection connection) throws SQLException {
        seedUser(connection, "student", "Workshop123!", "Workshop Student", "STUDENT");
        seedUser(connection, "trainer", "Trainer123!", "Workshop Trainer", "TRAINER");
    }

    private void seedUser(Connection connection, String username, String password, String displayName, String role) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT 1 FROM legacy_users WHERE username = ?")) {
            query.setString(1, username);
            try (ResultSet result = query.executeQuery()) {
                if (result.next()) {
                    return;
                }
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO legacy_users(username,password_hash,display_name,role) VALUES (?,?,?,?)")) {
            insert.setString(1, username);
            insert.setString(2, hasher.hash(password));
            insert.setString(3, displayName);
            insert.setString(4, role);
            insert.executeUpdate();
        }
    }

    public StoredUser findByUsername(String username) {
        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement statement = connection.prepareStatement(FIND_BY_USERNAME_SQL)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return mapUser(result);
                }
            }
        } catch (Exception error) {
            error.printStackTrace();
        }
        return null;
    }

    private StoredUser mapUser(ResultSet result) throws SQLException {
        UserRecord identity = new UserRecord(result.getLong("id"), result.getString("username"),
                result.getString("display_name"), result.getString("role"));
        return new StoredUser(identity, result.getString("password_hash"));
    }
}
