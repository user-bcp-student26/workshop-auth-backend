package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void generatesDifferentSaltsAndVerifiableHashes() {
        String first = hasher.hash("SyntheticPassword");
        String second = hasher.hash("SyntheticPassword");
        assertNotEquals(first, second);
        assertTrue(first.startsWith("v1$pbkdf2-sha256$600000$"));
        assertTrue(hasher.isValidHash(first));
        assertTrue(hasher.verify("SyntheticPassword", first));
        assertTrue(hasher.verify("SyntheticPassword", second));
        assertFalse(hasher.verify("incorrect", first));
        assertFalse(first.contains("SyntheticPassword"));
    }

    @Test
    void preservesUnicodeSpacesAndLongPasswords() {
        String password = " contrase\u00f1a \u4e16\u754c ".repeat(12);
        String encoded = hasher.hash(password);
        assertTrue(hasher.verify(password, encoded));
        assertFalse(hasher.verify(password.trim(), encoded));
        assertFalse(hasher.verify(password.substring(0, 72), encoded));
    }

    @Test
    void acceptsMaximumApprovedCost() {
        String encoded = new PasswordHasher(PasswordHasher.MAX_ITERATIONS).hash("maximum-cost");
        assertTrue(hasher.verify("maximum-cost", encoded));
    }

    @Test
    void rejectsConfigurationOutsideApprovedRange() {
        assertThrows(IllegalArgumentException.class, () -> new PasswordHasher(2));
        assertThrows(IllegalArgumentException.class, () -> new PasswordHasher(1200001));
    }

    @Test
    void rejectsNullPasswordForHashing() {
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(null));
    }

    @Test
    void rejectsNullMalformedAndUnknownFormats() {
        for (String malformed : new String[] {null, "", "plaintext", "x".repeat(161),
                "v2$pbkdf2-sha256$600000$AA==$AA==", "v1$sha256$600000$AA==$AA==",
                "v1$pbkdf2-sha256$600000$AA==$AA==$extra"}) {
            assertFalse(hasher.isValidHash(malformed));
            assertFalse(hasher.verify("test", malformed));
        }
    }

    @Test
    void rejectsMalformedCostAndBase64WithoutDeriving() {
        for (String cost : new String[] {"2", "1200001", "not-a-number", "999999999999999999", "0600000"}) {
            assertFalse(hasher.isValidHash("v1$pbkdf2-sha256$" + cost + "$AA==$AA=="));
        }
        assertFalse(hasher.isValidHash("v1$pbkdf2-sha256$600000$!$!"));
        assertFalse(hasher.isValidHash("v1$pbkdf2-sha256$600000$AA==$AA=="));
        String valid = hasher.hash("test");
        assertFalse(hasher.verify(null, valid));
        String[] fields = valid.split("\\$");
        assertFalse(hasher.isValidHash("v1$pbkdf2-sha256$600000$AA==$" + fields[4]));
        assertFalse(hasher.isValidHash("v1$pbkdf2-sha256$600000$" + fields[3] + "$AA=="));
    }
}