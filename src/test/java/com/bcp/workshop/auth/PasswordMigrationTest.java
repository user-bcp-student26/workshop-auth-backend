package com.bcp.workshop.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordMigrationTest {
    private Connection connection;

    @BeforeEach
    void openIsolatedDatabase() throws Exception {
        Class.forName("org.sqlite.JDBC");
        Path dir = Path.of("target", "migration-tests");
        Files.createDirectories(dir);
        String url = "jdbc:sqlite:" + dir.resolve("migration-" + UUID.randomUUID() + ".db").toAbsolutePath();
        connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
        }
    }

    @AfterEach
    void closeDatabase() throws Exception {
        connection.close();
    }

    @Test
    void migratesPlainTextPasswordsToVerifiableHashes() throws Exception {
        insert("alice", "Alice123!");
        insert("bob", "Bob123!");

        PasswordMigration.migrate(connection);

        assertFalse(stored("alice").equals("Alice123!"));
        assertTrue(PasswordHasher.verify("Alice123!", stored("alice")));
        assertTrue(PasswordHasher.verify("Bob123!", stored("bob")));
    }

    @Test
    void rollsBackWholeMigrationWhenAnyRowFails() throws Exception {
        insert("alice", "Alice123!");
        insert("bad", "Bad123!");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TRIGGER fail_bad BEFORE UPDATE ON legacy_users WHEN NEW.username='bad' BEGIN SELECT RAISE(ABORT,'boom'); END");
        }

        assertThrows(SQLException.class, () -> PasswordMigration.migrate(connection));

        assertEquals("Alice123!", stored("alice"));
        assertEquals("Bad123!", stored("bad"));
    }

    @Test
    void refusesMigrationAboveTenThousandUsersWithoutChangingRows() throws Exception {
        connection.setAutoCommit(false);
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)")) {
            for (int i = 0; i <= PasswordMigration.MAX_USERS; i++) {
                statement.setString(1, "u" + i);
                statement.setString(2, "p" + i);
                statement.setString(3, "n");
                statement.setString(4, "STUDENT");
                statement.addBatch();
            }
            statement.executeBatch();
        }
        connection.commit();

        assertThrows(IllegalStateException.class, () -> PasswordMigration.migrate(connection));

        assertEquals("p0", stored("u0"));
    }

    private void insert(String username, String password) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)")) {
            statement.setString(1, username);
            statement.setString(2, password);
            statement.setString(3, username);
            statement.setString(4, "STUDENT");
            statement.executeUpdate();
        }
    }

    private String stored(String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT password FROM legacy_users WHERE username=?")) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }
}
