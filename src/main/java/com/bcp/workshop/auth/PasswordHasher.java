package com.bcp.workshop.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

final class PasswordHasher {
    static final String PREFIX = "pbkdf2_sha256";
    static final int ITERATIONS = 600000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {}

    static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        try {
            byte[] key = derive(password, salt, ITERATIONS);
            return PREFIX + "$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(key);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Password hashing failed", error);
        }
    }

    static boolean verify(String password, String stored) {
        if (!isHashed(stored)) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            return MessageDigest.isEqual(expected, derive(password, salt, iterations));
        } catch (IllegalArgumentException | GeneralSecurityException error) {
            return false;
        }
    }

    static boolean isHashed(String stored) {
        return stored != null && stored.startsWith(PREFIX + "$");
    }

    private static byte[] derive(String password, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
