package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.text.StringEscapeUtils;

import java.util.Map;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final String NULL_REQUEST = "Request is null";
    private static final String REQUIRED_USERNAME = "Username is required";
    private static final String REQUIRED_PASSWORD = "Password is required";
    private static final String DEBUG_FLOW = "authenticated-with-legacy-flow";

    @Inject
    AuthService service;

    @Inject
    AuthTokenService tokens;

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        try {
            Response invalid = validateRequest(request);
            if (invalid != null) {
                return invalid;
            }
            UserRecord user = service.authenticate(request.username, request.password);
            if (user == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(Map.of("message", "Invalid user " + request.username + " or password")).build();
            }
            return successfulLogin(user);
        } catch (Exception error) {
            return Response.status(500).entity(Map.of(
                    "message", error.getMessage() == null ? "Unknown error" : error.getMessage(),
                    "exception", error.getClass().getName()
            )).build();
        }
    }

    private Response validateRequest(LoginRequest request) {
        if (request == null) {
            return badRequest(NULL_REQUEST);
        }
        if (request.username == null || request.username.trim().isEmpty()) {
            return badRequest(REQUIRED_USERNAME);
        }
        if (request.password == null || request.password.trim().isEmpty()) {
            return badRequest(REQUIRED_PASSWORD);
        }
        return null;
    }

    private Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("message", message)).build();
    }

    private Response successfulLogin(UserRecord user) {
        return Response.ok(Map.of(
                "token", tokens.create(user.username(), user.role()),
                "expiresIn", AuthTokenService.LIFETIME_SECONDS,
                "debug", DEBUG_FLOW,
                "user", Map.of("username", user.username(),
                        "displayName", StringEscapeUtils.escapeHtml4(user.displayName()), "role", user.role())
        )).build();
    }
}
