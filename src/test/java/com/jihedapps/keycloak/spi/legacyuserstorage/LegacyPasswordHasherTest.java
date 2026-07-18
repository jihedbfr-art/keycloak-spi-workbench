package com.jihedapps.keycloak.spi.legacyuserstorage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyPasswordHasherTest {

    private final LegacyPasswordHasher hasher = new LegacyPasswordHasher.Sha256SaltedHasher();

    @Test
    void matchesTheCorrectPasswordAgainstItsSaltedHash() {
        String salt = "a1b2c3";
        String hash = sha256Hex(salt + "correct horse battery staple");

        assertThat(hasher.matches("correct horse battery staple", hash, salt)).isTrue();
    }

    @Test
    void rejectsTheWrongPassword() {
        String salt = "a1b2c3";
        String hash = sha256Hex(salt + "correct horse battery staple");

        assertThat(hasher.matches("wrong password", hash, salt)).isFalse();
    }

    @Test
    void handlesANullSaltAsAnEmptyString() {
        String hash = sha256Hex("no salt here");

        assertThat(hasher.matches("no salt here", hash, null)).isTrue();
    }

    @Test
    void nullRawPasswordOrHashNeverMatches() {
        assertThat(hasher.matches(null, "somehash", "salt")).isFalse();
        assertThat(hasher.matches("password", null, "salt")).isFalse();
    }

    @Test
    void comparisonIsCaseInsensitiveOnTheHexHash() {
        String salt = "xyz";
        String hash = sha256Hex(salt + "password123").toUpperCase();

        assertThat(hasher.matches("password123", hash, salt)).isTrue();
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
