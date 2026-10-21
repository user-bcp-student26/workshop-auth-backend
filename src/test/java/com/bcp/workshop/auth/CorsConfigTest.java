package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class CorsConfigTest {
    @Test
    void allowsListedOrigin() {
        given().header("Origin", "http://localhost:4200")
                .when().get("/q/health")
                .then().statusCode(200)
                .header("Access-Control-Allow-Origin", "http://localhost:4200");
    }

    @Test
    void omitsAllowOriginForUnlistedOrigin() {
        Response response = given().header("Origin", "https://evil.example")
                .when().get("/q/health");
        assertNull(response.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void doesNotAllowUnlistedMethodInPreflight() {
        Response response = given().header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "PUT")
                .when().options("/auth/login");
        String methods = response.getHeader("Access-Control-Allow-Methods");
        assertTrue(methods == null || !methods.contains("PUT"));
    }
}
