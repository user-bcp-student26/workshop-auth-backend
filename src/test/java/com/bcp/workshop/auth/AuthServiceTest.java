package com.bcp.workshop.auth;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    private static final PasswordHasher HASHER = new PasswordHasher();
    private static final UserRecord IDENTITY = new UserRecord(7, " original ", "Synthetic User", "TRAINER");
    private static String hash;

    @BeforeAll
    static void prepareHash() {
        hash = HASHER.hash(" password \u4e16\u754c");
    }

    @Test
    void returnsOnlyIdentityAndUsesOriginalUsername() {
        LegacyDatabase repository = new LegacyDatabase() {
            @Override
            public StoredUser findByUsername(String username) {
                assertEquals(" original ", username);
                return new StoredUser(IDENTITY, hash);
            }
        };
        assertEquals(IDENTITY, new AuthService(repository, HASHER).authenticate(" original ", " password \u4e16\u754c"));
    }

    @Test
    void rejectsWrongOrTrimmedPassword() {
        AuthService service = serviceWith(hash);
        assertNull(service.authenticate(" original ", "incorrect"));
        assertNull(service.authenticate(" original ", "password \u4e16\u754c"));
    }

    @Test
    void rejectsCorruptHashWithoutPlaintextFallback() {
        assertNull(serviceWith("password").authenticate(" original ", "password"));
        assertNull(serviceWith(null).authenticate(" original ", "password"));
    }

    @Test
    void rejectsMissingUserWithoutCallingHasher() {
        LegacyDatabase missing = new LegacyDatabase() {
            @Override
            public StoredUser findByUsername(String username) { return null; }
        };
        PasswordHasher forbidden = new PasswordHasher() {
            @Override
            public boolean verify(String password, String encoded) { throw new AssertionError("Unexpected verification"); }
        };
        assertNull(new AuthService(missing, forbidden).authenticate("missing", "incorrect"));
    }

    @Test
    void propagatesUnexpectedFailureForResourceHandling() {
        LegacyDatabase broken = new LegacyDatabase() {
            @Override
            public StoredUser findByUsername(String username) { throw new IllegalStateException("Synthetic failure"); }
        };
        assertThrows(IllegalStateException.class, () -> new AuthService(broken, HASHER).authenticate("user", "password"));
    }

    @Test
    void storedUserDoesNotExposeHashInToString() {
        assertEquals("StoredUser[credentials=redacted]", new StoredUser(IDENTITY, hash).toString());
    }

    private AuthService serviceWith(String encoded) {
        LegacyDatabase repository = new LegacyDatabase() {
            @Override
            public StoredUser findByUsername(String username) { return new StoredUser(IDENTITY, encoded); }
        };
        return new AuthService(repository, HASHER);
    }
}