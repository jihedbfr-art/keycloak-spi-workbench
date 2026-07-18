package com.jihedapps.keycloak.spi.legacyuserstorage;

import java.util.regex.Pattern;

/**
 * Table/column names for the legacy store. These come from provider config (an operator typing
 * them into the admin console), not end-user input, but JDBC can't parameterize identifiers
 * either way — {@link #requireSafeIdentifier} is a guardrail against a typo or copy-paste error
 * turning into a SQL injection footgun, not a defense against a malicious admin.
 */
public record LegacyTableConfig(
        String tableName,
        String idColumn,
        String usernameColumn,
        String emailColumn,
        String firstNameColumn,
        String lastNameColumn,
        String passwordHashColumn,
        String passwordSaltColumn,
        String enabledColumn
) {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public LegacyTableConfig {
        requireSafeIdentifier(tableName, "tableName");
        requireSafeIdentifier(idColumn, "idColumn");
        requireSafeIdentifier(usernameColumn, "usernameColumn");
        requireSafeIdentifier(emailColumn, "emailColumn");
        requireSafeIdentifier(firstNameColumn, "firstNameColumn");
        requireSafeIdentifier(lastNameColumn, "lastNameColumn");
        requireSafeIdentifier(passwordHashColumn, "passwordHashColumn");
        requireSafeIdentifier(passwordSaltColumn, "passwordSaltColumn");
        requireSafeIdentifier(enabledColumn, "enabledColumn");
    }

    private static void requireSafeIdentifier(String value, String field) {
        if (value == null || !SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    field + " must be a plain SQL identifier (letters, digits, underscore, not starting "
                            + "with a digit), got: " + value);
        }
    }

    static LegacyTableConfig defaults(String tableName) {
        return new LegacyTableConfig(tableName, "id", "username", "email",
                "first_name", "last_name", "password_hash", "password_salt", "enabled");
    }
}
