package com.jihedapps.keycloak.spi.legacyuserstorage;

import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.models.UserModel;

import java.util.List;
import java.util.stream.Stream;

/**
 * Bridges {@link org.keycloak.models.UserModel#credentialManager()} back to the storage
 * provider's {@link CredentialInputValidator} — the standard way a read-only federated provider
 * plugs into Keycloak's credential checks without a local credential store of its own. Every
 * write operation is a no-op or returns false: this store never persists credentials, the legacy
 * table is the only place a password hash lives.
 */
class LegacyCredentialManager implements SubjectCredentialManager {

    private final RealmModel realm;
    private final UserModel user;
    private final CredentialInputValidator validator;

    LegacyCredentialManager(RealmModel realm, UserModel user, CredentialInputValidator validator) {
        this.realm = realm;
        this.user = user;
        this.validator = validator;
    }

    @Override
    public boolean isValid(List<CredentialInput> inputs) {
        return inputs.stream().allMatch(input -> validator.isValid(realm, user, input));
    }

    @Override
    public boolean updateCredential(CredentialInput input) {
        return false;
    }

    @Override
    public void updateStoredCredential(CredentialModel cred) {
        // read-only store — nothing to update
    }

    @Override
    public CredentialModel createStoredCredential(CredentialModel cred) {
        throw new UnsupportedOperationException("legacy-user-storage does not store credentials locally");
    }

    @Override
    public boolean removeStoredCredentialById(String id) {
        return false;
    }

    @Override
    public CredentialModel getStoredCredentialById(String id) {
        return null;
    }

    @Override
    public Stream<CredentialModel> getStoredCredentialsStream() {
        return Stream.empty();
    }

    @Override
    public Stream<CredentialModel> getStoredCredentialsByTypeStream(String type) {
        return Stream.empty();
    }

    @Override
    public CredentialModel getStoredCredentialByNameAndType(String name, String type) {
        return null;
    }

    @Override
    public boolean moveStoredCredentialTo(String id, String newPreviousCredentialId) {
        return false;
    }

    @Override
    public void updateCredentialLabel(String credentialId, String credentialLabel) {
        // no local credential storage — nothing to label
    }

    @Override
    public void disableCredentialType(String credentialType) {
        // no local credential storage — nothing to disable
    }

    @Override
    public Stream<String> getDisableableCredentialTypesStream() {
        return Stream.empty();
    }

    @Override
    public boolean isConfiguredFor(String type) {
        return validator.isConfiguredFor(realm, user, type);
    }

    @Override
    public boolean isConfiguredLocally(String type) {
        return false;
    }

    @Override
    public Stream<String> getConfiguredUserStorageCredentialTypesStream() {
        return validator.supportsCredentialType("password") ? Stream.of("password") : Stream.empty();
    }

    @Override
    public CredentialModel createCredentialThroughProvider(CredentialModel model) {
        throw new UnsupportedOperationException("legacy-user-storage does not store credentials locally");
    }
}
