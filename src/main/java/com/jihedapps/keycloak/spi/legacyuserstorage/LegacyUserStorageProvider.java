package com.jihedapps.keycloak.spi.legacyuserstorage;

import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Federates a legacy subscriber/user table read-only. Deliberately no
 * {@code UserRegistrationProvider} or {@code CredentialInputUpdater} — this provider never
 * writes back to the legacy store. That's the realistic shape of a migration where the legacy
 * system stays the source of truth for existing accounts while new accounts go straight into
 * Keycloak, rather than trying to keep two systems in sync in both directions.
 */
public class LegacyUserStorageProvider implements UserStorageProvider,
        UserLookupProvider, UserQueryProvider, CredentialInputValidator {

    private final KeycloakSession session;
    private final ComponentModel model;
    private final LegacyUserRepository repository;
    private final LegacyPasswordHasher hasher;

    public LegacyUserStorageProvider(KeycloakSession session, ComponentModel model,
                                      LegacyUserRepository repository, LegacyPasswordHasher hasher) {
        this.session = session;
        this.model = model;
        this.repository = repository;
        this.hasher = hasher;
    }

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        String username = StorageId.externalId(id);
        return repository.findByUsername(username)
                .map(u -> new LegacyUserAdapter(session, realm, model, u, this))
                .orElse(null);
    }

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        return repository.findByUsername(username)
                .map(u -> new LegacyUserAdapter(session, realm, model, u, this))
                .orElse(null);
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        return repository.findByEmail(email)
                .map(u -> new LegacyUserAdapter(session, realm, model, u, this))
                .orElse(null);
    }

    @Override
    public int getUsersCount(RealmModel realm, boolean includeServiceAccount) {
        return repository.count();
    }

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm, Map<String, String> params, Integer firstResult, Integer maxResults) {
        String search = params.getOrDefault(UserModel.SEARCH,
                params.getOrDefault(UserModel.USERNAME, params.get(UserModel.EMAIL)));
        return repository.search(search, firstResult, maxResults).stream()
                .map(u -> new LegacyUserAdapter(session, realm, model, u, this));
    }

    @Override
    public Stream<UserModel> getGroupMembersStream(RealmModel realm, GroupModel group, Integer firstResult, Integer maxResults) {
        // this provider doesn't manage group membership — the legacy table has no concept of it
        return Stream.empty();
    }

    @Override
    public Stream<UserModel> searchForUserByUserAttributeStream(RealmModel realm, String attrName, String attrValue) {
        if (UserModel.USERNAME.equals(attrName)) {
            UserModel user = getUserByUsername(realm, attrValue);
            return user != null ? Stream.of(user) : Stream.empty();
        }
        return Stream.empty();
    }

    @Override
    public boolean supportsCredentialType(String credentialType) {
        return PasswordCredentialModel.TYPE.equals(credentialType);
    }

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        return supportsCredentialType(credentialType);
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput input) {
        if (!supportsCredentialType(input.getType())) {
            return false;
        }
        Optional<LegacyUser> legacyUser = repository.findByUsername(user.getUsername());
        return legacyUser
                .map(u -> hasher.matches(input.getChallengeResponse(), u.passwordHash(), u.passwordSalt()))
                .orElse(false);
    }

    @Override
    public void close() {
        // repository holds no long-lived resources of its own — connections are per-call
    }
}
