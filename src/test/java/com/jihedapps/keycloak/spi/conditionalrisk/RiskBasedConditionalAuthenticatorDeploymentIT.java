package com.jihedapps.keycloak.spi.conditionalrisk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The point of this test isn't the authenticator's logic (that's covered by the unit tests) —
 * it's proving the jar this project actually produces deploys cleanly on a real Keycloak server
 * and shows up where an admin would look for it. Building an SPI that compiles is easy; building
 * one that a fresh Keycloak instance loads without a stack trace on startup is the part every
 * "here's my custom authenticator" blog post skips.
 *
 * <p>Runs against the shaded/plain jar built by {@code mvn package} — bound to the
 * integration-test phase via failsafe so the jar exists before this runs. Skipped during a plain
 * {@code mvn test}.
 */
@Testcontainers
class RiskBasedConditionalAuthenticatorDeploymentIT {

    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.6";

    @Container
    private final GenericContainer<?> keycloak = new GenericContainer<>(KEYCLOAK_IMAGE)
            .withCopyFileToContainer(
                    MountableFile.forHostPath(builtJarPath()),
                    "/opt/keycloak/providers/keycloak-spi-workbench.jar")
            .withEnv("KEYCLOAK_ADMIN", "admin")
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
            .withExposedPorts(8080)
            .withCommand("start-dev")
            .waitingFor(Wait.forLogMessage(".*Running the server in development mode.*\\n", 1)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    private static String builtJarPath() {
        String version = "0.1.0";
        File jar = new File("target/keycloak-spi-workbench-" + version + ".jar");
        if (!jar.exists()) {
            throw new IllegalStateException(
                    "Built jar not found at " + jar.getAbsolutePath()
                            + " — this test needs `mvn package` (or `verify`) to have run first, "
                            + "not a bare `mvn test`.");
        }
        return jar.getAbsolutePath();
    }

    @Test
    void customAuthenticatorFactoryIsRegisteredOnServerStartup() throws Exception {
        String baseUrl = "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
        HttpClient http = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();

        String accessToken = fetchAdminAccessToken(http, mapper, baseUrl);

        HttpRequest providersRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/admin/realms/master/authentication/authenticator-providers"))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        HttpResponse<String> providersResponse =
                http.send(providersRequest, HttpResponse.BodyHandlers.ofString());

        assertThat(providersResponse.statusCode()).isEqualTo(200);

        JsonNode providers = mapper.readTree(providersResponse.body());
        boolean found = false;
        for (JsonNode provider : providers) {
            if (RiskBasedConditionalAuthenticatorFactory.PROVIDER_ID.equals(provider.path("id").asText())) {
                found = true;
                assertThat(provider.path("displayName").asText()).contains("Risk-Based Step-Up");
                break;
            }
        }
        assertThat(found)
                .as("expected '%s' in the server's registered authenticator providers",
                        RiskBasedConditionalAuthenticatorFactory.PROVIDER_ID)
                .isTrue();
    }

    private String fetchAdminAccessToken(HttpClient http, ObjectMapper mapper, String baseUrl) throws Exception {
        HttpRequest tokenRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/realms/master/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "grant_type=password&client_id=admin-cli&username=admin&password=admin"))
                .build();
        HttpResponse<String> tokenResponse =
                http.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(tokenResponse.statusCode())
                .as("admin token request failed: %s", tokenResponse.body())
                .isEqualTo(200);
        return mapper.readTree(tokenResponse.body()).path("access_token").asText();
    }
}
