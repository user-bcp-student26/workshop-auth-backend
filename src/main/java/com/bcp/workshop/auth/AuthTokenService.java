package com.bcp.workshop.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;

@Startup
@ApplicationScoped
public class AuthTokenService {
    public static final int LIFETIME_SECONDS = 3600;
    private static final int MIN_SECRET_LENGTH = 32;
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    private final Algorithm algorithm;

    @Inject
    public AuthTokenService(@ConfigProperty(name = "auth.token.secret") String secret) {
        this.algorithm = Algorithm.HMAC256(requireSecret(secret));
    }

    public String create(String username, String role) {
        Instant issuedAt = Instant.now();
        return JWT.create()
                .withClaim(CLAIM_USERNAME, username)
                .withClaim(CLAIM_ROLE, role)
                .withIssuedAt(issuedAt)
                .withExpiresAt(issuedAt.plusSeconds(LIFETIME_SECONDS))
                .sign(algorithm);
    }

    public VerifiedToken verify(String token) {
        DecodedJWT decoded = JWT.require(algorithm).build().verify(token);
        return new VerifiedToken(decoded.getClaim(CLAIM_USERNAME).asString(), decoded.getClaim(CLAIM_ROLE).asString());
    }

    private static String requireSecret(String secret) {
        if (secret == null || secret.isBlank() || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("auth.token.secret must be set to at least " + MIN_SECRET_LENGTH + " characters");
        }
        return secret;
    }

    public record VerifiedToken(String username, String role) {
    }
}
