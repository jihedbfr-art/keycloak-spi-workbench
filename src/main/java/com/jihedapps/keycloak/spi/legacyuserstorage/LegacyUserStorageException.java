package com.jihedapps.keycloak.spi.legacyuserstorage;

/** Wraps {@link java.sql.SQLException} so callers aren't forced to handle a checked exception
 * for what's, in practice, an unrecoverable failure of the legacy store during a request. */
public class LegacyUserStorageException extends RuntimeException {

    public LegacyUserStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
