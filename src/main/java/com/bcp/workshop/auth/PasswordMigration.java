package com.bcp.workshop.auth;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

final class PasswordMigration {
    static final int MAX_USERS = 10000;

    private PasswordMigration() {}

    static void migrate(Connection connection) throws SQLException {
        int total;
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM legacy_users")) {
            result.next();
            total = result.getInt(1);
        }
        if (total > MAX_USERS) {
            throw new IllegalStateException("Refusing to migrate " + total + " users; limit is " + MAX_USERS);
        }

        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            List<PlainPassword> pending = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT id, password FROM legacy_users")) {
                while (result.next()) {
                    String password = result.getString("password");
                    if (!PasswordHasher.isHashed(password)) {
                        pending.add(new PlainPassword(result.getLong("id"), password));
                    }
                }
            }
            try (PreparedStatement update = connection.prepareStatement("UPDATE legacy_users SET password=? WHERE id=?")) {
                for (PlainPassword entry : pending) {
                    update.setString(1, PasswordHasher.hash(entry.password()));
                    update.setLong(2, entry.id());
                    update.executeUpdate();
                }
            }
            connection.commit();
        } catch (SQLException | RuntimeException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private record PlainPassword(long id, String password) {}
}
