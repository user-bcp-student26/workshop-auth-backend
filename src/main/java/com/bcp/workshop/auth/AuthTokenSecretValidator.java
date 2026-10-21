package com.bcp.workshop.auth;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class AuthTokenSecretValidator {
    private static final int MIN_BYTES = 32;

    @ConfigProperty(name = "auth.token.secret", defaultValue = "")
    String secret;

    void onStart(@Observes StartupEvent event) {
        validate(secret);
    }

    static void validate(String secret) {
        if (secret != null && !secret.isEmpty() && secret.getBytes(StandardCharsets.UTF_8).length < MIN_BYTES) {
            throw new IllegalStateException("auth.token.secret must be at least 32 bytes");
        }
    }
}
