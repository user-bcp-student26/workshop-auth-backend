package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class LegacyDatabaseStorageTest {
    @Test
    void seededPasswordsAreStoredAsHashes() throws SQLException {
        assertFalse(stored("student").equals("Workshop123!"));
        assertTrue(stored("student").startsWith("pbkdf2_sha256$"));
        assertTrue(stored("trainer").startsWith("pbkdf2_sha256$"));
    }

    @Test
    void seededUsersGetDistinctSalts() throws SQLException {
        assertNotEquals(stored("student").split("\\$")[2], stored("trainer").split("\\$")[2]);
    }

    @Test
    void malformedStoredHashIsRejectedWith401() throws SQLException {
        try (Connection connection = DriverManager.getConnection(System.getenv("AUTH_DB_URL"));
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR IGNORE INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)")) {
            statement.setString(1, "broken-hash");
            statement.setString(2, "pbkdf2_sha256$bad");
            statement.setString(3, "Broken Hash");
            statement.setString(4, "STUDENT");
            statement.executeUpdate();
        }
        given().contentType("application/json").body(Map.of("username", "broken-hash", "password", "anything"))
                .when().post("/auth/login").then().statusCode(401)
                .body("token", nullValue());
    }

    @Test
    void loginResponseDoesNotExposePasswordMaterial() {
        given().contentType("application/json").body(Map.of("username", "student", "password", "Workshop123!"))
                .when().post("/auth/login").then().statusCode(200)
                .body("password", nullValue())
                .body("user.password", nullValue())
                .body("user.id", nullValue())
                .body("user.salt", nullValue())
                .body("debug", is("authenticated-with-legacy-flow"));
    }

    private static String stored(String username) throws SQLException {
        try (Connection connection = DriverManager.getConnection(System.getenv("AUTH_DB_URL"));
             PreparedStatement statement = connection.prepareStatement("SELECT password FROM legacy_users WHERE username=?")) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }
}
