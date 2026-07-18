package com.jihedapps.keycloak.spi.legacyuserstorage;

/**
 * One row from the legacy table, already mapped out of the {@link java.sql.ResultSet}. Keeping
 * this as a plain holder (not a {@link org.keycloak.models.UserModel}) is what makes the mapping
 * and password-check logic testable without touching Keycloak's model classes at all.
 */
public record LegacyUser(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        String passwordHash,
        String passwordSalt,
        boolean enabled
) {
}
