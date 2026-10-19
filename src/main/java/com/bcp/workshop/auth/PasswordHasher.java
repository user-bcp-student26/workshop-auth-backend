package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@ApplicationScoped
public class PasswordHasher {
    static final int DEFAULT_ITERATIONS = 600000;
    static final int MIN_ITERATIONS = 600000;
    static final int MAX_ITERATIONS = 1200000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final int MAX_ENCODED_LENGTH = 160;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String VERSION = "v1";
    private static final String FORMAT_ALGORITHM = "pbkdf2-sha256";
    private final SecureRandom random = new SecureRandom();
    private final int iterations;

    public PasswordHasher() {
        this(DEFAULT_ITERATIONS);
    }

    @Inject
    public PasswordHasher(@ConfigProperty(name = "auth.password.iterations", defaultValue = "600000") int iterations) {
        if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) {
            throw new IllegalArgumentException("Password hashing cost outside approved range");
        }
        this.iterations = iterations;
        try {
            SecretKeyFactory.getInstance(ALGORITHM);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Password hashing provider unavailable", error);
        }
    }

    public String hash(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password is required");
        }
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] derived = derive(password, salt, iterations);
        try {
            return VERSION + "$" + FORMAT_ALGORITHM + "$" + iterations + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(derived);
        } finally {
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(derived, (byte) 0);
        }
    }

    public boolean isValidHash(String encoded) {
        return parse(encoded) != null;
    }

    public boolean verify(String password, String encoded) {
        ParsedHash parsed = parse(encoded);
        if (password == null || parsed == null) {
            return false;
        }
        byte[] derived = derive(password, parsed.salt(), parsed.iterations());
        try {
            return MessageDigest.isEqual(parsed.hash(), derived);
        } finally {
            Arrays.fill(derived, (byte) 0);
            Arrays.fill(parsed.salt(), (byte) 0);
            Arrays.fill(parsed.hash(), (byte) 0);
        }
    }

    private ParsedHash parse(String encoded) {
        if (encoded == null || encoded.length() > MAX_ENCODED_LENGTH) {
            return null;
        }
        String[] fields = encoded.split("\\$", -1);
        if (fields.length != 5 || !VERSION.equals(fields[0]) || !FORMAT_ALGORITHM.equals(fields[1])) {
            return null;
        }
        try {
            int cost = Integer.parseInt(fields[2]);
            if (cost < MIN_ITERATIONS || cost > MAX_ITERATIONS || !Integer.toString(cost).equals(fields[2])) {
                return null;
            }
            byte[] salt = Base64.getDecoder().decode(fields[3]);
            byte[] hash = Base64.getDecoder().decode(fields[4]);
            if (salt.length != SALT_BYTES || hash.length != HASH_BYTES) {
                return null;
            }
            return new ParsedHash(cost, salt, hash);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private byte[] derive(String password, byte[] salt, int cost) {
        char[] characters = password.toCharArray();
        PBEKeySpec specification = new PBEKeySpec(characters, salt, cost, HASH_BYTES * Byte.SIZE);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(specification).getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Password hashing failed", error);
        } finally {
            specification.clearPassword();
            Arrays.fill(characters, '\0');
        }
    }

    private record ParsedHash(int iterations, byte[] salt, byte[] hash) {}
}