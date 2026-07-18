package com.jihedapps.keycloak.spi.legacyuserstorage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Provider-level logic (credential validation, search delegation) isolated from real JDBC —
 * {@link LegacyUserRepositoryTest} and the Postgres IT already cover the SQL layer, so this test
 * fakes {@link LegacyUserRepository} instead of standing up another database.
 */
class LegacyUserStorageProviderTest {

    private KeycloakSession session;
    private RealmModel realm;
    private ComponentModel model;
    private LegacyUserRepository repository;
    private LegacyUserStorageProvider provider;

    private static final LegacyUser ACTIVE_USER = new LegacyUser(
            "1", "jsmith", "jsmith@example.com", "John", "Smith", "hash1", "salt1", true);

    @BeforeEach
    void setUp() {
        session = mock(KeycloakSession.class);
        realm = mock(RealmModel.class);
        model = mock(ComponentModel.class);
        when(model.getId()).thenReturn("legacy-storage-component-1");
        repository = mock(LegacyUserRepository.class);

        provider = new LegacyUserStorageProvider(
                session, model, repository, new LegacyPasswordHasher.Sha256SaltedHasher());
    }

    @Test
    void getUserByUsernameWrapsARepositoryHitInAnAdapter() {
        when(repository.findByUsername("jsmith")).thenReturn(Optional.of(ACTIVE_USER));

        UserModel user = provider.getUserByUsername(realm, "jsmith");

        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo("jsmith");
        assertThat(user.getEmail()).isEqualTo("jsmith@example.com");
    }

    @Test
    void getUserByUsernameReturnsNullOnAMiss() {
        when(repository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThat(provider.getUserByUsername(realm, "nobody")).isNull();
    }

    @Test
    void isValidAcceptsTheCorrectLegacyPassword() {
        LegacyUser withRealHash = new LegacyUser("1", "jsmith", "jsmith@example.com",
                "John", "Smith", sha256Hex("salt1" + "correct-password"), "salt1", true);
        when(repository.findByUsername("jsmith")).thenReturn(Optional.of(withRealHash));

        UserModel user = mock(UserModel.class);
        when(user.getUsername()).thenReturn("jsmith");
        CredentialInput input = mock(CredentialInput.class);
        when(input.getType()).thenReturn(PasswordCredentialModel.TYPE);
        when(input.getChallengeResponse()).thenReturn("correct-password");

        assertThat(provider.isValid(realm, user, input)).isTrue();
    }

    @Test
    void isValidRejectsTheWrongPassword() {
        when(repository.findByUsername("jsmith")).thenReturn(Optional.of(ACTIVE_USER));

        UserModel user = mock(UserModel.class);
        when(user.getUsername()).thenReturn("jsmith");
        CredentialInput input = mock(CredentialInput.class);
        when(input.getType()).thenReturn(PasswordCredentialModel.TYPE);
        when(input.getChallengeResponse()).thenReturn("wrong-password");

        assertThat(provider.isValid(realm, user, input)).isFalse();
    }

    @Test
    void isValidRejectsAnUnsupportedCredentialType() {
        UserModel user = mock(UserModel.class);
        CredentialInput input = mock(CredentialInput.class);
        when(input.getType()).thenReturn("otp");

        assertThat(provider.isValid(realm, user, input)).isFalse();
    }

    @Test
    void searchDelegatesTheSearchTermFromParams() {
        when(repository.search("maria", 0, 10)).thenReturn(java.util.List.of());

        provider.searchForUserStream(realm, Map.of(UserModel.SEARCH, "maria"), 0, 10).toList();

        org.mockito.Mockito.verify(repository).search("maria", 0, 10);
    }

    private String sha256Hex(String input) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
