package com.bcp.workshop.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class PasswordMigrationTest {
    private final PasswordHasher hasher = new PasswordHasher();
    private final PasswordMigration migration = new PasswordMigration(hasher);
    private Path database;

    @BeforeEach
    void prepareDatabase() throws Exception {
        DriverManager.registerDriver(new org.sqlite.JDBC());
        database = Files.createTempDirectory(Path.of("target"), "h02-migration-").resolve("users.db");
    }

    @Test
    void createsSecureSchemaDirectly() throws Exception {
        assertNull(migration.migrate(database));
        assertEquals(0, scalar("SELECT COUNT(*) FROM legacy_users"));
        assertEquals(1, scalar("SELECT version FROM auth_schema_version"));
        assertThrows(SQLException.class, () -> text("SELECT password FROM legacy_users"));
    }

    @Test
    void migratesAndRestoresConsistentBackupWithIdsAndSequence() throws Exception {
        createLegacy();
        execute("INSERT INTO legacy_users VALUES (50,'deleted','synthetic','Deleted','STUDENT')");
        execute("DELETE FROM legacy_users WHERE id=50");
        Path backup = migration.migrate(database);
        assertNotNull(backup);
        assertTrue(Files.exists(backup));
        String encoded = text("SELECT password_hash FROM legacy_users WHERE id=1");
        assertTrue(hasher.verify(" password \u4e16\u754c", encoded));
        assertEquals("legacy", text("SELECT username FROM legacy_users WHERE id=1"));
        assertEquals("Legacy User", text("SELECT display_name FROM legacy_users WHERE id=1"));
        assertEquals("TRAINER", text("SELECT role FROM legacy_users WHERE id=1"));
        assertEquals(50, scalar("SELECT seq FROM sqlite_sequence WHERE name='legacy_users'"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM sqlite_master WHERE name='legacy_users_h02'"));
        assertThrows(SQLException.class, () -> text("SELECT password FROM legacy_users"));
        Path restored = database.resolveSibling("restored.db");
        Files.copy(backup, restored, StandardCopyOption.REPLACE_EXISTING);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + restored);
             Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT password FROM legacy_users WHERE id=1")) {
            assertTrue(result.next());
            assertEquals(" password \u4e16\u754c", result.getString(1));
        }
    }

    @Test
    void rerunDoesNotRehashOrCreateAnotherBackup() throws Exception {
        createLegacy();
        migration.migrate(database);
        String before = text("SELECT password_hash FROM legacy_users");
        assertNull(migration.migrate(database));
        assertEquals(before, text("SELECT password_hash FROM legacy_users"));
    }

    @Test
    void preservesSequenceWhenAllLegacyUsersWereDeleted() throws Exception {
        createLegacy();
        execute("INSERT INTO legacy_users VALUES (50,'deleted','synthetic','Deleted','STUDENT')");
        execute("DELETE FROM legacy_users");
        migration.migrate(database);
        assertEquals(50, scalar("SELECT seq FROM sqlite_sequence WHERE name='legacy_users'"));
    }

    @Test
    void preservesCustomIndexTriggerAndUniqueConstraint() throws Exception {
        createLegacy();
        execute("CREATE TABLE audit(name TEXT)");
        execute("CREATE INDEX display_index ON legacy_users(display_name)");
        execute("CREATE TRIGGER audit_user AFTER INSERT ON legacy_users BEGIN INSERT INTO audit VALUES(NEW.display_name); END");
        migration.migrate(database);
        assertEquals(1, scalar("SELECT COUNT(*) FROM sqlite_master WHERE name='display_index'"));
        execute("INSERT INTO legacy_users(username,password_hash,display_name,role) SELECT 'second',password_hash,'Second','STUDENT' FROM legacy_users WHERE id=1");
        assertEquals("Second", text("SELECT name FROM audit"));
        assertThrows(SQLException.class, () -> execute("INSERT INTO legacy_users(username,password_hash,display_name,role) SELECT username,password_hash,display_name,role FROM legacy_users WHERE id=1"));
    }

    @Test
    void rollsBackWholeRebuildWhenLegacyIndexCannotBePreserved() throws Exception {
        createLegacy();
        execute("CREATE INDEX plaintext_index ON legacy_users(password)");
        assertThrows(SQLException.class, () -> migration.migrate(database));
        assertEquals(" password \u4e16\u754c", text("SELECT password FROM legacy_users WHERE id=1"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM sqlite_master WHERE name='plaintext_index'"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM sqlite_master WHERE name='legacy_users_h02'"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM sqlite_master WHERE name='auth_schema_version'"));
    }

    @Test
    void rejectsUnknownSchemaWithoutChanges() throws Exception {
        execute("CREATE TABLE legacy_users(id INTEGER)");
        assertThrows(SQLException.class, () -> migration.migrate(database));
        assertEquals(1, scalar("SELECT COUNT(*) FROM pragma_table_info('legacy_users')"));
    }

    @Test
    void rejectsOversizedOfflineBatchBeforeHashing() throws Exception {
        createLegacy();
        execute("WITH RECURSIVE users(number) AS (SELECT 1 UNION ALL SELECT number+1 FROM users WHERE number<10000) "
                + "INSERT INTO legacy_users(username,password,display_name,role) SELECT 'bulk-'||number,'synthetic','Bulk','STUDENT' FROM users");
        assertThrows(SQLException.class, () -> migration.migrate(database));
        assertEquals(10001, scalar("SELECT COUNT(*) FROM legacy_users"));
        assertEquals(" password \u4e16\u754c", text("SELECT password FROM legacy_users WHERE id=1"));
    }

    @Test
    void rejectsInvalidStoredHashesAndFutureVersion() throws Exception {
        migration.migrate(database);
        execute("INSERT INTO legacy_users(username,password_hash,display_name,role) VALUES ('bad','corrupt','Bad','STUDENT')");
        assertThrows(SQLException.class, () -> migration.migrate(database));
        execute("DELETE FROM legacy_users");
        execute("UPDATE auth_schema_version SET version=99");
        assertThrows(SQLException.class, () -> migration.migrate(database));
    }

    @Test
    void refusesConcurrentMigrationWithoutChangingDatabase() throws Exception {
        createLegacy();
        Path lockPath = database.resolveSibling("users.db.migration.lock");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.lock()) {
            assertThrows(IllegalStateException.class, () -> migration.migrate(database));
        }
        assertEquals(" password \u4e16\u754c", text("SELECT password FROM legacy_users"));
    }

    private void createLegacy() throws Exception {
        execute("CREATE TABLE legacy_users(id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)")) {
            statement.setString(1, "legacy");
            statement.setString(2, " password \u4e16\u754c");
            statement.setString(3, "Legacy User");
            statement.setString(4, "TRAINER");
            statement.executeUpdate();
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + database);
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private String text(String sql) throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            return result.getString(1);
        }
    }

    private long scalar(String sql) throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            return result.getLong(1);
        }
    }
}