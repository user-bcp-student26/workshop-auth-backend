package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class PasswordMigration {
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_MIGRATION_USERS = 10000;
    private static final String CREATE_USERS = "CREATE TABLE %s (id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "username TEXT UNIQUE, password_hash TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)";
    private final PasswordHasher hasher;

    public PasswordMigration() {
        this(new PasswordHasher());
    }

    @Inject
    public PasswordMigration(PasswordHasher hasher) {
        this.hasher = hasher;
    }

    public Path migrate(Path databasePath) throws IOException, SQLException {
        Path absolute = databasePath.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        Path lockPath = absolute.resolveSibling(absolute.getFileName() + ".migration.lock");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = acquireLock(channel);
             Connection connection = DriverManager.getConnection("jdbc:sqlite:" + absolute)) {
            Map<String, Column> columns = readColumns(connection);
            if (columns.isEmpty()) {
                createFreshSchema(connection);
                return null;
            }
            boolean legacy = columns.containsKey("password");
            validateSchema(connection, columns, legacy);
            if (!legacy) {
                validateVersionAndHashes(connection);
                return null;
            }
            if (rowCount(connection) > MAX_MIGRATION_USERS) {
                throw new SQLException("Authentication migration exceeds offline batch limit");
            }
            Path backup = backup(connection, absolute);
            List<MigratedUser> users = hashLegacyUsers(connection);
            rebuild(connection, users);
            return backup;
        }
    }

    private FileLock acquireLock(FileChannel channel) throws IOException {
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                throw new IllegalStateException("Another password migration is running");
            }
            return lock;
        } catch (OverlappingFileLockException busy) {
            throw new IllegalStateException("Another password migration is running", busy);
        }
    }

    private Map<String, Column> readColumns(Connection connection) throws SQLException {
        Map<String, Column> columns = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA table_info(legacy_users)")) {
            while (result.next()) {
                columns.put(result.getString("name"), new Column(result.getString("type"),
                        result.getInt("notnull"), result.getInt("pk")));
            }
        }
        return columns;
    }

    private void validateSchema(Connection connection, Map<String, Column> columns, boolean legacy) throws SQLException {
        String credential = legacy ? "password" : "password_hash";
        Map<String, Column> expected = Map.of("id", new Column("INTEGER", 0, 1),
                "username", new Column("TEXT", 0, 0), credential, new Column("TEXT", 1, 0),
                "display_name", new Column("TEXT", 1, 0), "role", new Column("TEXT", 1, 0));
        if (!expected.equals(columns) || !hasUniqueUsername(connection)) {
            throw new SQLException("Unsupported authentication schema");
        }
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='legacy_users'")) {
            if (!result.next() || !result.getString(1).toUpperCase(java.util.Locale.ROOT).contains("AUTOINCREMENT")) {
                throw new SQLException("Authentication schema requires AUTOINCREMENT");
            }
        }
    }

    private boolean hasUniqueUsername(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet indexes = statement.executeQuery("SELECT name FROM pragma_index_list('legacy_users') WHERE \"unique\"=1")) {
            while (indexes.next()) {
                try (PreparedStatement statementColumns = connection.prepareStatement("SELECT name FROM pragma_index_info(?)")) {
                    statementColumns.setString(1, indexes.getString(1));
                    try (ResultSet names = statementColumns.executeQuery()) {
                        if (names.next() && "username".equals(names.getString(1)) && !names.next()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void createFreshSchema(Connection connection) throws SQLException {
        transaction(connection, () -> {
            execute(connection, CREATE_USERS.formatted("legacy_users"));
            writeVersion(connection);
        });
    }

    private Path backup(Connection connection, Path databasePath) throws IOException, SQLException {
        Path backup = Files.createTempFile(databasePath.getParent(), databasePath.getFileName() + ".pre-h02-", ".db");
        restrictBackup(backup);
        try (PreparedStatement statement = connection.prepareStatement("VACUUM INTO ?")) {
            statement.setString(1, backup.toString());
            statement.execute();
        }
        return backup;
    }

    private void restrictBackup(Path path) throws IOException {
        if (Files.getFileAttributeView(path, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (acl == null) {
            throw new IOException("Backup filesystem cannot enforce owner-only permissions");
        }
        AclEntry entry = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(path))
                .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build();
        acl.setAcl(List.of(entry));
    }

    private List<MigratedUser> hashLegacyUsers(Connection connection) throws SQLException {
        List<MigratedUser> users = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT id, username, password, display_name, role FROM legacy_users")) {
            while (result.next()) {
                if (users.size() >= MAX_MIGRATION_USERS) {
                    throw new SQLException("Authentication migration exceeds offline batch limit");
                }
                users.add(new MigratedUser(result.getLong("id"), result.getString("username"),
                        hasher.hash(result.getString("password")), result.getString("display_name"), result.getString("role")));
            }
        }
        return users;
    }

    private void rebuild(Connection connection, List<MigratedUser> users) throws SQLException {
        List<String> schemaObjects = schemaObjects(connection);
        long sequence = sequence(connection);
        transaction(connection, () -> {
            execute(connection, CREATE_USERS.formatted("legacy_users_h02"));
            insertMigratedUsers(connection, users);
            execute(connection, "DROP TABLE legacy_users");
            execute(connection, "ALTER TABLE legacy_users_h02 RENAME TO legacy_users");
            for (String definition : schemaObjects) {
                execute(connection, definition);
            }
            restoreSequence(connection, sequence);
            writeVersion(connection);
            validateVersionAndHashes(connection);
            if (rowCount(connection) != users.size()) {
                throw new SQLException("Authentication migration row count mismatch");
            }
        });
    }

    private List<String> schemaObjects(Connection connection) throws SQLException {
        List<String> definitions = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
                "SELECT sql FROM sqlite_master WHERE tbl_name='legacy_users' AND type IN ('index','trigger') AND sql IS NOT NULL")) {
            while (result.next()) {
                definitions.add(result.getString(1));
            }
        }
        return definitions;
    }

    private long sequence(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT seq FROM sqlite_sequence WHERE name='legacy_users'")) {
            return result.next() ? result.getLong(1) : 0;
        }
    }

    private void restoreSequence(Connection connection, long previous) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE sqlite_sequence SET seq=MAX(seq,?) WHERE name='legacy_users'")) {
            statement.setLong(1, previous);
            if (statement.executeUpdate() == 0) {
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO sqlite_sequence(name,seq) VALUES ('legacy_users',?)")) {
                    insert.setLong(1, previous);
                    insert.executeUpdate();
                }
            }
        }
    }

    private void insertMigratedUsers(Connection connection, List<MigratedUser> users) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO legacy_users_h02(id,username,password_hash,display_name,role) VALUES (?,?,?,?,?)")) {
            for (MigratedUser user : users) {
                statement.setLong(1, user.id());
                statement.setString(2, user.username());
                statement.setString(3, user.hash());
                statement.setString(4, user.displayName());
                statement.setString(5, user.role());
                statement.executeUpdate();
            }
        }
    }

    private void writeVersion(Connection connection) throws SQLException {
        execute(connection, "CREATE TABLE IF NOT EXISTS auth_schema_version(version INTEGER NOT NULL)");
        execute(connection, "DELETE FROM auth_schema_version");
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO auth_schema_version(version) VALUES (?)")) {
            statement.setInt(1, SCHEMA_VERSION);
            statement.executeUpdate();
        }
    }

    private void validateVersionAndHashes(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT version FROM auth_schema_version")) {
            if (!result.next() || result.getInt(1) != SCHEMA_VERSION || result.next()) {
                throw new SQLException("Unsupported authentication schema version");
            }
        }
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT password_hash FROM legacy_users")) {
            while (result.next()) {
                if (!hasher.isValidHash(result.getString(1))) {
                    throw new SQLException("Invalid stored authentication hash");
                }
            }
        }
    }

    private long rowCount(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM legacy_users")) {
            return result.getLong(1);
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private void transaction(Connection connection, SqlWork work) throws SQLException {
        connection.setAutoCommit(false);
        try {
            work.execute();
            connection.commit();
        } catch (SQLException | RuntimeException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @FunctionalInterface
    private interface SqlWork { void execute() throws SQLException; }
    private record Column(String type, int notNull, int primaryKey) {}
    private record MigratedUser(long id, String username, String hash, String displayName, String role) {}
}