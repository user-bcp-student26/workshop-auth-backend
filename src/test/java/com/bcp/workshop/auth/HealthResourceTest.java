package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class HealthResourceTest {
    @Test
    void returnsHealthWithoutAuthentication() {
        given().accept("application/json")
                .when().get("/q/health").then().statusCode(200).contentType("application/json")
                .body("", is(Map.of("status", "UP", "checks", List.of())));
    }

    @Test
    void acceptsEmptyJsonBody() {
        given().contentType("application/json").body("{}")
                .when().get("/q/health").then().statusCode(200).contentType("application/json")
                .body("", is(Map.of("status", "UP", "checks", List.of())));
    }

    @Test
    void returnsJsonDespiteXmlAcceptHeader() {
        given().accept("application/xml")
                .when().get("/q/health").then().statusCode(200).contentType("application/json")
                .body("", is(Map.of("status", "UP", "checks", List.of())));
    }

    @Test
    void characterizesDocumentedPostCompatibility() {
        given().when().post("/q/health").then().statusCode(200).contentType("application/json")
                .body("", is(Map.of("status", "UP", "checks", List.of())));
    }
}