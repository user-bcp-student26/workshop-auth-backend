package com.bcp.workshop.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public final class LegacyTokenUtil {
    private LegacyTokenUtil() {}

    public static String create(String username, String role, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("auth.token.secret is not configured");
        }
        long expiry = Instant.now().plusSeconds(3600).getEpochSecond();
        String value = username + "|" + role + "|" + expiry + "|" + secret;
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
