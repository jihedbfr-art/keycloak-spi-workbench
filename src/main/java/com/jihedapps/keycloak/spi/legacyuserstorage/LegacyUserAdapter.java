package com.jihedapps.keycloak.spi.legacyuserstorage;

import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.adapter.AbstractUserAdapter;

/**
 * Wraps one {@link LegacyUser} row as a read-only {@link org.keycloak.models.UserModel}. The
 * username doubles as the external ID (see {@code AbstractUserAdapter#getId()} default), which
 * is fine here because the legacy table's username column is already the natural lookup key —
 * no separate internal ID scheme to keep in sync.
 */
public class LegacyUserAdapter extends AbstractUserAdapter {

    private final LegacyUser user;
    private final CredentialInputValidator credentialValidator;

    public LegacyUserAdapter(KeycloakSession session, RealmModel realm, ComponentModel model, LegacyUser user,
                              CredentialInputValidator credentialValidator) {
        super(session, realm, model);
        this.storageId = new StorageId(storageProviderModel.getId(), user.username());
        this.user = user;
        this.credentialValidator = credentialValidator;
    }

    @Override
    public String getUsername() {
        return user.username();
    }

    @Override
    public String getEmail() {
        return user.email();
    }

    @Override
    public String getFirstName() {
        return user.firstName();
    }

    @Override
    public String getLastName() {
        return user.lastName();
    }

    @Override
    public boolean isEnabled() {
        return user.enabled();
    }

    @Override
    public SubjectCredentialManager credentialManager() {
        return new LegacyCredentialManager(realm, this, credentialValidator);
    }
}
