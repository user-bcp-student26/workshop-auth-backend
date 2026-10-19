package com.bcp.workshop.auth;

public record StoredUser(UserRecord identity, String passwordHash) {
    @Override
    public String toString() {
        return "StoredUser[credentials=redacted]";
    }
}