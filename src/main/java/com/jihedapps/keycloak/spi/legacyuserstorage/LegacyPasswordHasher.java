package com.jihedapps.keycloak.spi.legacyuserstorage;

/**
 * Verifies a raw password against whatever hash format the legacy system actually used. There is
 * no universal answer here — every BSS/CRM system invented its own scheme over the decades, and
 * getting this wrong silently locks every user out (or worse, accepts wrong passwords). Swap
 * {@link Sha256SaltedHasher} for whatever your legacy system really does before pointing this at
 * production data.
 */
public interface LegacyPasswordHasher {

    boolean matches(String rawPassword, String storedHash, String storedSalt);

    /**
     * SHA-256(salt + password), hex-encoded. A common enough legacy pattern to use as the
     * default, but treat it as a placeholder, not a recommendation — it's here so the provider
     * has *something* that works out of the box for the tests and the demo realm, not because
     * it's a hash scheme to imitate for anything new.
     */
    final class Sha256SaltedHasher implements LegacyPasswordHasher {

        @Override
        public boolean matches(String rawPassword, String storedHash, String storedSalt) {
            if (rawPassword == null || storedHash == null) {
                return false;
            }
            String salt = storedSalt != null ? storedSalt : "";
            String computed = sha256Hex(salt + rawPassword);
            return computed.equalsIgnoreCase(storedHash);
        }

        private String sha256Hex(String input) {
            try {
                var digest = java.security.MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder(hash.length * 2);
                for (byte b : hash) {
                    hex.append(String.format("%02x", b));
                }
                return hex.toString();
            } catch (java.security.NoSuchAlgorithmException e) {
                // SHA-256 is guaranteed present on every JVM — this branch is unreachable in practice
                throw new IllegalStateException("SHA-256 not available", e);
            }
        }
    }
}
