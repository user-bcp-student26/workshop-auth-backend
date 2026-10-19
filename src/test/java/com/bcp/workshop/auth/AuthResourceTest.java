package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AuthResourceTest {
    @Inject
    LegacyDatabase database;

    @Inject
    AuthService service;

    @BeforeEach
    void prepareDatabase() throws Exception {
        LegacyDatabaseTest.resetDatabase();
    }

    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("token", notNullValue());
    }

    @Test
    void characterizesStudentResponseAndLegacyToken() {
        assertSuccessfulLogin("student", "Workshop123!", "Workshop Student", "STUDENT");
    }

    @Test
    void characterizesTrainerResponse() {
        assertSuccessfulLogin("trainer", "Trainer123!", "Workshop Trainer", "TRAINER");
    }

    @Test
    void characterizesIgnoredClientAndUnknownProperties() {
        Map<String, Object> body = Map.of("username", "student", "password", "Workshop123!",
                "client", "different-client", "extra", "ignored");
        given().contentType("application/json").body(body)
                .when().post("/auth/login").then().statusCode(200)
                .body("user.username", is("student"));
    }

    @Test
    void characterizesUsernameValidationAndPrecedence() {
        assertError(Map.of("password", ""), 400, "Username is required");
        assertError(Map.of("username", "", "password", ""), 400, "Username is required");
        assertError(Map.of("username", "  ", "password", ""), 400, "Username is required");
    }

    @Test
    void characterizesPasswordValidation() {
        assertError(Map.of("username", "student"), 400, "Password is required");
        assertError(Map.of("username", "student", "password", ""), 400, "Password is required");
        assertError(Map.of("username", "student", "password", "  "), 400, "Password is required");
    }

    @Test
    void characterizesInvalidCredentialsMessage() {
        assertError(Map.of("username", "missing", "password", "incorrect"),
                401, "Invalid user missing or password");
        assertError(Map.of("username", "student", "password", "incorrect"),
                401, "Invalid user student or password");
        assertError(Map.of("username", " student ", "password", "Workshop123!"),
                401, "Invalid user  student  or password");
    }

    @Test
    void characterizesHtmlEscapingAndOriginalPasswords() throws Exception {
        LegacyDatabaseTest.insertUser(" spaced ", " password ", "<Student> & \"Trainer\"", "TRAINER");
        assertSuccessfulLogin(" spaced ", " password ", "&lt;Student&gt; &amp; &quot;Trainer&quot;", "TRAINER");
        assertError(Map.of("username", " spaced ", "password", "password"),
                401, "Invalid user  spaced  or password");
    }

    @Test
    void characterizesSqlFailureAsUnauthorized() throws Exception {
        try (Connection connection = LegacyDatabaseTest.openTestConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE legacy_users");
        }
        assertError(Map.of("username", "student", "password", "Workshop123!"),
                401, "Invalid user student or password");
    }

    @Test
    void characterizesNullRequestDirectly() {
        AuthResource resource = new AuthResource();
        resource.service = service;
        try (Response response = resource.login(null)) {
            assertEquals(400, response.getStatus());
            assertEquals(Map.of("message", "Request is null"), response.getEntity());
        }
    }

    @Test
    void characterizesUnexpectedExceptionMessageDirectly() {
        assertInternalError(new IllegalStateException("Synthetic failure"), "Synthetic failure");
    }

    @Test
    void characterizesNullExceptionMessageDirectly() {
        assertInternalError(new IllegalStateException(), "Unknown error");
    }

    @Test
    void rejectsNullJsonRequest() {
        given().contentType("application/json").body("null")
                .when().post("/auth/login").then().statusCode(400)
                .body("", is(Map.of("message", "Request is null")));
    }

    @Test
    void rejectsMissingRequestBody() {
        given().contentType("application/json")
                .when().post("/auth/login").then().statusCode(400)
                .body("", is(Map.of("message", "Request is null")));
    }

    @Test
    void rejectsExplicitlyNullUsername() {
        given().contentType("application/json").body("{\"username\":null,\"password\":\"valid\"}")
                .when().post("/auth/login").then().statusCode(400)
                .body("", is(Map.of("message", "Username is required")));
    }

    @Test
    void rejectsExplicitlyNullPassword() {
        given().contentType("application/json").body("{\"username\":\"student\",\"password\":null}")
                .when().post("/auth/login").then().statusCode(400)
                .body("", is(Map.of("message", "Password is required")));
    }

    @Test
    void rejectsMalformedJson() {
        assertEmptyFrameworkError(given().contentType("application/json").body("{")
                .when().post("/auth/login"), 400);
    }

    @Test
    void rejectsIncompatibleJsonShape() {
        assertEmptyFrameworkError(given().contentType("application/json").body("[]")
                .when().post("/auth/login"), 400);
    }

    @Test
    void rejectsUnsupportedMediaType() {
        assertEmptyFrameworkError(given().contentType("text/plain").body("test")
                .when().post("/auth/login"), 415);
    }

    @Test
    void rejectsUnacceptableResponseMediaType() {
        assertEmptyFrameworkError(given().contentType("application/json").accept("application/xml")
                .body(Map.of("username", "student", "password", "Workshop123!"))
                .when().post("/auth/login"), 406);
    }

    @Test
    void rejectsWrongHttpMethod() {
        assertEmptyFrameworkError(given().when().get("/auth/login"), 405);
    }

    @Test
    void returnsDocumented500ForSyntheticInconsistentRecord() throws Exception {
        try (Connection connection = LegacyDatabaseTest.openTestConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE legacy_users");
            statement.executeUpdate("CREATE TABLE legacy_users(id INTEGER PRIMARY KEY, username TEXT, password_hash TEXT, display_name TEXT, role TEXT)");
        }
        LegacyDatabaseTest.insertUser("inconsistent", "SyntheticPassword", null, "STUDENT");
        Map<String, Object> response = given().contentType("application/json")
                .body(Map.of("username", "inconsistent", "password", "SyntheticPassword"))
                .when().post("/auth/login").then().statusCode(500).contentType("application/json")
                .extract().jsonPath().getMap("$");
        assertEquals(Map.of("message", "Unknown error", "exception", NullPointerException.class.getName()), response);
    }

    @Test
    @Tag("correction")
    void authenticatesQuotedCredentialsWithoutChangingHttpContract() throws Exception {
        LegacyDatabaseTest.insertUser("o'brien", "pass'word", "Quoted User", "STUDENT");
        assertSuccessfulLogin("o'brien", "pass'word", "Quoted User", "STUDENT");
    }

    @Test
    @Tag("h02-integration")
    void authenticatesMigratedLegacyUserWithUnchangedHttpContract() throws Exception {
        try (Connection connection = LegacyDatabaseTest.openTestConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE legacy_users");
            statement.executeUpdate("DROP TABLE auth_schema_version");
            statement.executeUpdate("CREATE TABLE legacy_users(id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO legacy_users(id,username,password,display_name,role) VALUES (?,?,?,?,?)")) {
                insert.setLong(1, 42);
                insert.setString(2, "legacy-user");
                insert.setString(3, " legacy-\u4e16\u754c ");
                insert.setString(4, "<Legacy> & User");
                insert.setString(5, "TRAINER");
                insert.executeUpdate();
            }
        }
        database.initialize();
        assertSuccessfulLogin("legacy-user", " legacy-\u4e16\u754c ", "&lt;Legacy&gt; &amp; User", "TRAINER");
        assertEquals(42, database.findByUsername("legacy-user").identity().id());
    }

    @Test
    @Tag("h02-integration")
    void rejectsCorruptHashWithoutExposingDetailsOrFallingBack() throws Exception {
        try (Connection connection = LegacyDatabaseTest.openTestConnection(); PreparedStatement update = connection.prepareStatement(
                "UPDATE legacy_users SET password_hash=? WHERE username=?")) {
            update.setString(1, "Workshop123!");
            update.setString(2, "student");
            update.executeUpdate();
        }
        assertError(Map.of("username", "student", "password", "Workshop123!"),
                401, "Invalid user student or password");
    }

    private void assertSuccessfulLogin(String username, String password, String displayName, String role) {
        long beforeLogin = Instant.now().getEpochSecond();
        Map<String, Object> response = given().contentType("application/json")
                .body(Map.of("username", username, "password", password))
                .when().post("/auth/login").then().statusCode(200).contentType("application/json")
                .body("expiresIn", is(3600)).body("debug", is("authenticated-with-legacy-flow"))
                .body("user", is(Map.of("username", username, "displayName", displayName, "role", role)))
                .extract().jsonPath().getMap("$");
        assertEquals(java.util.Set.of("token", "expiresIn", "user", "debug"), response.keySet());
        String[] tokenParts = new String(Base64.getDecoder().decode((String) response.get("token")),
                StandardCharsets.UTF_8).split("\\|", -1);
        assertEquals(4, tokenParts.length);
        assertEquals(username, tokenParts[0]);
        assertEquals(role, tokenParts[1]);
        long expiry = Long.parseLong(tokenParts[2]);
        assertTrue(expiry >= beforeLogin + 3600);
        assertTrue(expiry <= Instant.now().getEpochSecond() + 3600);
    }

    private void assertError(Map<String, String> request, int status, String message) {
        Map<String, Object> response = given().contentType("application/json").body(request)
                .when().post("/auth/login").then().statusCode(status).contentType("application/json")
                .extract().jsonPath().getMap("$");
        assertEquals(Map.of("message", message), response);
    }

    private void assertEmptyFrameworkError(io.restassured.response.Response response, int status) {
        assertEquals(status, response.statusCode());
        assertEquals(0, response.asByteArray().length);
        assertNull(response.header("Content-Type"));
    }

    private void assertInternalError(IllegalStateException failure, String message) {
        AuthResource resource = new AuthResource();
        resource.service = new AuthService(database, new PasswordHasher()) {
            @Override
            public UserRecord authenticate(String username, String password) {
                throw failure;
            }
        };
        LoginRequest request = new LoginRequest();
        request.username = "synthetic";
        request.password = "SyntheticPassword";
        try (Response response = resource.login(request)) {
            assertEquals(500, response.getStatus());
            assertEquals(Map.of("message", message, "exception", IllegalStateException.class.getName()),
                    response.getEntity());
        }
    }
}
