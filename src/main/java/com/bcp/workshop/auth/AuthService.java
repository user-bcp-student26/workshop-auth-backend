package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AuthService {
    private final LegacyDatabase repository;
    private final PasswordHasher hasher;

    @Inject
    public AuthService(LegacyDatabase repository, PasswordHasher hasher) {
        this.repository = repository;
        this.hasher = hasher;
    }

    public UserRecord authenticate(String username, String password) {
        StoredUser stored = repository.findByUsername(username);
        if (stored == null || !hasher.verify(password, stored.passwordHash())) {
            return null;
        }
        return stored.identity();
    }
}