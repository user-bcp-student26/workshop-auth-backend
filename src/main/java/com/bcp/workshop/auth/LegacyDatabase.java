package com.bcp.workshop.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@ApplicationScoped
public class LegacyDatabase {
    private static final String DB_URL = System.getenv().getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(Path.of("data"));
            try (Connection connection = DriverManager.getConnection(DB_URL); Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
                seed(connection, "student", "Workshop123!", "Workshop Student", "STUDENT");
                seed(connection, "trainer", "Trainer123!", "Workshop Trainer", "TRAINER");
                PasswordMigration.migrate(connection);
            }
        } catch (Exception error) {
            throw new IllegalStateException("Legacy database initialization failed", error);
        }
    }

    private static void seed(Connection connection, String username, String password, String displayName, String role) throws SQLException {
        try (PreparedStatement exists = connection.prepareStatement("SELECT 1 FROM legacy_users WHERE username=?")) {
            exists.setString(1, username);
            try (ResultSet result = exists.executeQuery()) {
                if (result.next()) {
                    return;
                }
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)")) {
            insert.setString(1, username);
            insert.setString(2, PasswordHasher.hash(password));
            insert.setString(3, displayName);
            insert.setString(4, role);
            insert.executeUpdate();
        }
    }

    public UserRecord findByCredentials(String username, String password) {
        String sql = "SELECT id, username, display_name, role, password FROM legacy_users WHERE username=?";
        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next() && PasswordHasher.verify(password, result.getString("password"))) {
                    return new UserRecord(result.getLong("id"), result.getString("username"), result.getString("display_name"), result.getString("role"));
                }
            }
        } catch (Exception error) {
            error.printStackTrace();
        }
        return null;
    }
}
