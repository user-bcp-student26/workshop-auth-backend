package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthTokenSecretValidatorTest {
    @Test
    void rejectsSecretShorterThan32Bytes() {
        assertThrows(IllegalStateException.class, () -> AuthTokenSecretValidator.validate("short"));
    }

    @Test
    void acceptsSecretOf32Bytes() {
        assertDoesNotThrow(() -> AuthTokenSecretValidator.validate("a".repeat(32)));
    }

    @Test
    void acceptsMissingSecretBecauseLoginFailsClosed() {
        assertDoesNotThrow(() -> AuthTokenSecretValidator.validate(""));
    }
}
