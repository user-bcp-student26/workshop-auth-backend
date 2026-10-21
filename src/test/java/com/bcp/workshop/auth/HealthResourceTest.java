package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class HealthResourceTest {
    @Test
    void returnsUpWithoutAuthentication() {
        given().accept("application/json")
                .when().get("/q/health")
                .then().statusCode(200).contentType("application/json")
                .body("status", is("UP"))
                .body("checks", empty());
    }

    @Test
    void ignoresAuthorizationHeader() {
        given().header("Authorization", "Bearer test-only-not-a-token")
                .when().get("/q/health")
                .then().statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void returnsJsonDespiteXmlAcceptHeader() {
        given().accept("application/xml")
                .when().get("/q/health")
                .then().statusCode(200).contentType("application/json")
                .body("status", is("UP"));
    }

    @Test
    void answersPostWithSameBodyForCompatibility() {
        given().when().post("/q/health")
                .then().statusCode(200).contentType("application/json")
                .body("status", is("UP"))
                .body("checks", empty());
    }
}
