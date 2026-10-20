package com.bcp.workshop.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthTokenServiceTest {
    private static final String SECRET = "unit-test-secret-0123456789abcdefghij";
    private static final String OTHER_SECRET = "other-test-secret-0123456789abcdefgh";
    private static final String WEAK_SECRET = "short-weak-value";
    private final AuthTokenService service = new AuthTokenService(SECRET);

    @Test
    void verifiesTokenIssuedWithConfiguredSecret() {
        String token = service.create("student", "STUDENT");

        AuthTokenService.VerifiedToken identity = service.verify(token);

        assertEquals("student", identity.username());
        assertEquals("STUDENT", identity.role());
        long lifetime = JWT.decode(token).getExpiresAtAsInstant().getEpochSecond() - Instant.now().getEpochSecond();
        assertTrue(lifetime <= AuthTokenService.LIFETIME_SECONDS && lifetime >= AuthTokenService.LIFETIME_SECONDS - 5);
    }

    @Test
    void rejectsPayloadAlteredWithOriginalSignature() {
        String[] original = service.create("student", "STUDENT").split("\\.");
        String[] donor = service.create("student", "TRAINER").split("\\.");
        String forged = original[0] + "." + donor[1] + "." + original[2];

        assertThrows(JWTVerificationException.class, () -> service.verify(forged));
    }

    @Test
    void rejectsExpiredToken() {
        String expired = JWT.create()
                .withClaim("username", "student")
                .withClaim("role", "STUDENT")
                .withExpiresAt(Instant.now().minusSeconds(60))
                .sign(Algorithm.HMAC256(SECRET));

        assertThrows(JWTVerificationException.class, () -> service.verify(expired));
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        String foreign = JWT.create()
                .withClaim("username", "student")
                .withClaim("role", "STUDENT")
                .withExpiresAt(Instant.now().plusSeconds(600))
                .sign(Algorithm.HMAC256(OTHER_SECRET));

        assertThrows(JWTVerificationException.class, () -> service.verify(foreign));
    }

    @Test
    void rejectsLegacyBase64Token() {
        String legacy = Base64.getEncoder().encodeToString(
                ("student|STUDENT|" + (Instant.now().getEpochSecond() + 3600) + "|WORKSHOP_SECRET_2026")
                        .getBytes(StandardCharsets.UTF_8));

        assertThrows(JWTVerificationException.class, () -> service.verify(legacy));
    }

    @Test
    void rejectsMalformedToken() {
        assertThrows(JWTVerificationException.class, () -> service.verify("not-a-jwt"));
    }

    @Test
    void failsWithoutLeakingConfiguredValueWhenSecretIsMissingOrWeak() {
        assertThrows(IllegalStateException.class, () -> new AuthTokenService(null));
        assertThrows(IllegalStateException.class, () -> new AuthTokenService("   "));
        IllegalStateException weak = assertThrows(IllegalStateException.class, () -> new AuthTokenService(WEAK_SECRET));
        assertFalse(weak.getMessage().contains(WEAK_SECRET));
    }
}
