package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AuthResourceTest {
    private static final String LOGIN = "/auth/login";

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
    void loginsStudentWithDocumentedResponse() {
        Response response = given().contentType("application/json")
                .body(Map.of("username", "student", "password", "Workshop123!", "client", "angular-workshop"))
                .when().post(LOGIN);
        response.then().statusCode(200)
                .body("token", notNullValue())
                .body("expiresIn", is(3600))
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("debug", is("authenticated-with-legacy-flow"));
        assertEquals(Set.of("username", "displayName", "role"), response.jsonPath().getMap("user").keySet());
    }

    @Test
    void loginsTrainerWithTrainerRole() {
        given().contentType("application/json")
                .body(Map.of("username", "trainer", "password", "Trainer123!"))
                .when().post(LOGIN).then().statusCode(200)
                .body("user.username", is("trainer"))
                .body("user.role", is("TRAINER"));
    }

    @Test
    void rejectsMissingBodyAsNullRequest() {
        given().contentType("application/json")
                .when().post(LOGIN).then().statusCode(400)
                .body("message", is("Request is null"));
    }

    @Test
    void rejectsJsonNullAsNullRequest() {
        given().contentType("application/json").body("null")
                .when().post(LOGIN).then().statusCode(400)
                .body("message", is("Request is null"));
    }

    @Test
    void rejectsAbsentUsername() {
        given().contentType("application/json").body(Map.of("password", "Workshop123!"))
                .when().post(LOGIN).then().statusCode(400)
                .body("message", is("Username is required"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void rejectsNullEmptyOrBlankUsername(String username) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", "Workshop123!");
        given().contentType("application/json").body(body)
                .when().post(LOGIN).then().statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void usernameErrorTakesPrecedenceOverPasswordError() {
        given().contentType("application/json").body(Map.of("username", " ", "password", " "))
                .when().post(LOGIN).then().statusCode(400)
                .body("message", is("Username is required"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void rejectsNullEmptyOrBlankPassword(String password) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", "student");
        body.put("password", password);
        given().contentType("application/json").body(body)
                .when().post(LOGIN).then().statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsUnknownUserWith401WithoutToken() {
        given().contentType("application/json").body(Map.of("username", "nobody", "password", "Workshop123!"))
                .when().post(LOGIN).then().statusCode(401)
                .body("message", is("Invalid user nobody or password"))
                .body("token", nullValue());
    }

    @Test
    void rejectsWrongPasswordWith401WithoutToken() {
        given().contentType("application/json").body(Map.of("username", "student", "password", "wrong"))
                .when().post(LOGIN).then().statusCode(401)
                .body("message", is("Invalid user student or password"))
                .body("token", nullValue());
    }

    @Test
    void returnsEmptyBadRequestForMalformedJson() {
        assertEmptyFrameworkError(given().contentType("application/json").body("{")
                .when().post(LOGIN), 400);
    }

    @Test
    void returnsEmptyMethodNotAllowedForGet() {
        assertEmptyFrameworkError(given().when().get(LOGIN), 405);
    }

    @Test
    void returnsEmptyNotAcceptableForTextPlainAccept() {
        assertEmptyFrameworkError(given().contentType("application/json").accept("text/plain")
                .body(Map.of("username", "student", "password", "Workshop123!"))
                .when().post(LOGIN), 406);
    }

    @Test
    void returnsEmptyUnsupportedMediaTypeForTextPlainBody() {
        assertEmptyFrameworkError(given().contentType("text/plain").body("test")
                .when().post(LOGIN), 415);
    }

    private static void assertEmptyFrameworkError(Response response, int status) {
        assertEquals(status, response.getStatusCode());
        assertEquals(0, response.asByteArray().length);
        assertNull(response.getHeader("Content-Type"));
    }

    @Test
    void escapesDisplayNameWithHtml() throws Exception {
        try (Connection connection = DriverManager.getConnection(System.getenv("AUTH_DB_URL"));
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR IGNORE INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)")) {
            statement.setString(1, "html-user");
            statement.setString(2, PasswordHasher.hash("Html123!"));
            statement.setString(3, "<Student & Trainer>");
            statement.setString(4, "STUDENT");
            statement.executeUpdate();
        }
        given().contentType("application/json").body(Map.of("username", "html-user", "password", "Html123!"))
                .when().post(LOGIN).then().statusCode(200)
                .body("user.displayName", is("&lt;Student &amp; Trainer&gt;"));
    }

    @Test
    void returnsGeneric500WithoutInternalDetails() {
        assertInternalError(new IllegalStateException("Synthetic failure"));
    }

    @Test
    void returnsGeneric500WhenExceptionHasNoMessage() {
        assertInternalError(new IllegalStateException());
    }

    private static void assertInternalError(RuntimeException failure) {
        AuthResource resource = new AuthResource();
        resource.database = new LegacyDatabase() {
            @Override
            public UserRecord findByCredentials(String username, String password) {
                throw failure;
            }
        };
        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = "Workshop123!";
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger logger = Logger.getLogger(AuthResource.class.getName());
        logger.addHandler(handler);
        jakarta.ws.rs.core.Response response;
        try {
            response = resource.login(request);
        } finally {
            logger.removeHandler(handler);
        }
        assertEquals(500, response.getStatus());
        assertEquals(Map.of("message", "Internal error"), response.getEntity());
        assertTrue(records.stream().anyMatch(record -> record.getThrown() == failure));
    }

    @Test
    void returnsGeneric500AndNoTokenWhenSecretMissing() {
        AuthResource resource = new AuthResource();
        resource.database = new LegacyDatabase() {
            @Override
            public UserRecord findByCredentials(String username, String password) {
                return new UserRecord(1L, username, "Workshop Student", "STUDENT");
            }
        };
        resource.authTokenSecret = "";
        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = "Workshop123!";
        jakarta.ws.rs.core.Response response = resource.login(request);
        assertEquals(500, response.getStatus());
        assertEquals(Map.of("message", "Internal error"), response.getEntity());
    }

    @Test
    void rejectsSqlInjectionInUsername() {
        given().contentType("application/json").body(Map.of("username", "' OR '1'='1' --", "password", "x"))
                .when().post(LOGIN).then().statusCode(401)
                .body("token", nullValue());
    }

    @Test
    void authenticatesCredentialsContainingApostrophes() throws Exception {
        try (Connection connection = DriverManager.getConnection(System.getenv("AUTH_DB_URL"));
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR IGNORE INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)")) {
            statement.setString(1, "o'brien");
            statement.setString(2, PasswordHasher.hash("pa'ss"));
            statement.setString(3, "O'Brien");
            statement.setString(4, "STUDENT");
            statement.executeUpdate();
        }
        given().contentType("application/json").body(Map.of("username", "o'brien", "password", "pa'ss"))
                .when().post(LOGIN).then().statusCode(200)
                .body("user.username", is("o'brien"));
    }
}
