package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {
    @Test
    void hashUsesVersionedFormatAndNeverStoresPlainText() {
        String stored = PasswordHasher.hash("Workshop123!");
        assertTrue(stored.startsWith("pbkdf2_sha256$600000$"));
        assertFalse(stored.contains("Workshop123!"));
    }

    @Test
    void samePasswordGetsDifferentSalts() {
        assertNotEquals(PasswordHasher.hash("Workshop123!"), PasswordHasher.hash("Workshop123!"));
    }

    @Test
    void verifyAcceptsCorrectPasswordAndRejectsWrongOne() {
        String stored = PasswordHasher.hash("Workshop123!");
        assertTrue(PasswordHasher.verify("Workshop123!", stored));
        assertFalse(PasswordHasher.verify("wrong", stored));
    }

    @Test
    void verifyRejectsMalformedStoredValues() {
        assertFalse(PasswordHasher.verify("x", "pbkdf2_sha256$bad"));
        assertFalse(PasswordHasher.verify("x", "pbkdf2_sha256$600000$%%%$%%%"));
        assertFalse(PasswordHasher.verify("x", "Workshop123!"));
    }
}
