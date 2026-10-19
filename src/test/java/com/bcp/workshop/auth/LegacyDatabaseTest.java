package com.bcp.workshop.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyDatabaseTest {
    private static final PasswordHasher HASHER = new PasswordHasher();
    private final LegacyDatabase database = new LegacyDatabase();

    private UserRecord authenticate(String username, String password) {
        return new AuthService(database, HASHER).authenticate(username, password);
    }

    @BeforeEach
    void prepareDatabase() throws Exception {
        resetDatabase();
    }

    static Connection openTestConnection() throws Exception {
        String databaseUrl = System.getenv("AUTH_DB_URL");
        if (databaseUrl == null || !databaseUrl.startsWith("jdbc:sqlite:")) {
            throw new IllegalStateException("Tests require an isolated AUTH_DB_URL");
        }
        Path databasePath = Path.of(databaseUrl.substring("jdbc:sqlite:".length())).toAbsolutePath().normalize();
        if (!databasePath.startsWith(Path.of("target").toAbsolutePath().normalize())) {
            throw new IllegalStateException("Test database must be inside target");
        }
        try {
            DriverManager.getDriver(databaseUrl);
        } catch (SQLException missingDriver) {
            DriverManager.registerDriver(new org.sqlite.JDBC());
        }
        return DriverManager.getConnection(databaseUrl);
    }

    static void resetDatabase() throws Exception {
        try (Connection connection = openTestConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE IF EXISTS legacy_users");
            statement.executeUpdate("DROP TABLE IF EXISTS auth_schema_version");
        }
        new LegacyDatabase().initialize();
    }

    static void insertUser(String username, String password, String displayName, String role) throws Exception {
        String sql = "INSERT INTO legacy_users(username,password_hash,display_name,role) VALUES (?,?,?,?)";
        try (Connection connection = openTestConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, HASHER.hash(password));
            statement.setString(3, displayName);
            statement.setString(4, role);
            statement.executeUpdate();
        }
    }

    @Test
    void characterizesSeededUsers() {
        UserRecord student = authenticate("student", "Workshop123!");
        assertNotNull(student);
        assertEquals("student", student.username());
        assertEquals("Workshop Student", student.displayName());
        assertEquals("STUDENT", student.role());
        UserRecord trainer = authenticate("trainer", "Trainer123!");
        assertNotNull(trainer);
        assertEquals("TRAINER", trainer.role());
    }

    @Test
    void characterizesMissingUserAndWrongPassword() {
        assertNull(authenticate("missing", "Workshop123!"));
        assertNull(authenticate("student", "incorrect"));
    }

    @Test
    void characterizesOriginalCredentialsWithoutTrimming() throws Exception {
        insertUser(" spaced ", " password ", "Spaced User", "STUDENT");
        assertNotNull(authenticate(" spaced ", " password "));
        assertNull(authenticate("spaced", " password "));
        assertNull(authenticate(" spaced ", "password"));
        assertNull(authenticate("Student", "Workshop123!"));
    }

    @Test
    void characterizesUserRecordMapping() throws Exception {
        insertUser("mapped", "MappingPassword", "<Student> & Trainer", "TRAINER");
        UserRecord user = authenticate("mapped", "MappingPassword");
        assertNotNull(user);
        assertEquals(3, user.id());
        assertEquals("mapped", user.username());
        assertEquals("<Student> & Trainer", user.displayName());
        assertEquals("TRAINER", user.role());
    }

    @Test
    void characterizesIdempotentInitialization() throws Exception {
        try (Connection connection = openTestConnection(); PreparedStatement statement = connection.prepareStatement(
                "UPDATE legacy_users SET display_name=?, role=? WHERE username=?")) {
            statement.setString(1, "Changed Name");
            statement.setString(2, "TRAINER");
            statement.setString(3, "student");
            statement.executeUpdate();
        }
        UserRecord before = authenticate("student", "Workshop123!");
        database.initialize();
        assertEquals(before, authenticate("student", "Workshop123!"));
    }

    @Test
    void characterizesSqlFailureAsMissingUser() throws Exception {
        try (Connection connection = openTestConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE legacy_users");
        }
        assertNull(authenticate("student", "Workshop123!"));
    }

    @Test
    void rejectsInitializationFailureBeforeServingRequests() throws Exception {
        try (Connection connection = openTestConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE legacy_users");
            statement.executeUpdate("CREATE TABLE legacy_users(id INTEGER)");
        }
        assertThrows(IllegalStateException.class, database::initialize);
    }

    @Test
    @Tag("correction")
    void authenticatesUsernameContainingApostrophe() throws Exception {
        insertUser("o'brien", "QuotedUsernamePassword", "Quoted User", "STUDENT");
        UserRecord user = authenticate("o'brien", "QuotedUsernamePassword");
        assertNotNull(user);
        assertEquals("o'brien", user.username());
        assertNull(authenticate("obrien", "QuotedUsernamePassword"));
    }

    @Test
    @Tag("correction")
    void authenticatesPasswordContainingApostrophe() throws Exception {
        insertUser("quoted-password", "pass'word", "Quoted Password", "TRAINER");
        assertNotNull(authenticate("quoted-password", "pass'word"));
        assertNull(authenticate("quoted-password", "password"));
    }

    @Test
    @Tag("correction")
    void rejectsNonmatchingSqlPunctuationAsLiteralValues() {
        assertNull(authenticate("unknown';", "incorrect"));
        assertNull(authenticate("student", "incorrect';"));
    }

    @Test
    void characterizesUnicodeCredentials() throws Exception {
        insertUser("ni\u00f1o", "contrase\u00f1a \u4e16\u754c", "Unicode User", "STUDENT");
        assertNotNull(authenticate("ni\u00f1o", "contrase\u00f1a \u4e16\u754c"));
        assertNull(authenticate("ni\u00f1o", "contrasena \u4e16\u754c"));
    }

    @Test
    void characterizesLongPasswordsWithoutTruncation() throws Exception {
        String password = "long-password-".repeat(12);
        insertUser("long-password-user", password, "Long Password", "STUDENT");
        assertNotNull(authenticate("long-password-user", password));
        assertNull(authenticate("long-password-user", password.substring(0, 72)));
    }

    @Test
    @Tag("h02-correction")
    void storesOnlyVersionedHashes() throws Exception {
        Set<String> columns = new HashSet<>();
        try (Connection connection = openTestConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA table_info(legacy_users)")) {
            while (result.next()) {
                columns.add(result.getString("name"));
            }
        }
        assertFalse(columns.contains("password"), "Plaintext column must be removed");
        assertTrue(columns.contains("password_hash"));
        try (Connection connection = openTestConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT password_hash FROM legacy_users")) {
            int count = 0;
            while (result.next()) {
                assertTrue(result.getString("password_hash").startsWith("v1$pbkdf2-sha256$600000$"));
                count++;
            }
            assertEquals(2, count);
        }
    }
}