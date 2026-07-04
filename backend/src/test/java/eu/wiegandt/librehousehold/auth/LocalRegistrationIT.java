package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.auth.repository.AccountRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class LocalRegistrationIT {

    private static final String REDIRECT_URI = "http://localhost:5173/callback";
    private static final String CLIENT_ID = "librehousehold-spa";

    @Autowired
    RestTestClient restTestClient;

    @Autowired
    AccountRepository accountRepository;

    @Nested
    class registerLocal {

        @Test
        void withValidData_createsAccountAndHousehold() {
            // given
            var email = "register-" + UUID.randomUUID() + "@example.com";
            var requestBody = Map.of(
                    "householdName", "Test Haushalt",
                    "memberName", "Test Admin",
                    "email", email,
                    "password", "supersecret123"
            );

            // when
            var response = restTestClient.post()
                    .uri("/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .returnResult();

            // then
            assertThat(response.getResponseBody()).containsKeys("inviteToken", "household");
            assertThat(accountRepository.findByEmail(email)).isPresent();
        }

        @Test
        void withValidData_setsSessionCookieInResponse() {
            // given
            var email = "session-" + UUID.randomUUID() + "@example.com";
            var requestBody = Map.of(
                    "householdName", "Session Haushalt",
                    "memberName", "Session Admin",
                    "email", email,
                    "password", "supersecret123"
            );

            // when
            var response = restTestClient.post()
                    .uri("/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .exchange()
                    .expectStatus().isOk()
                    .returnResult();

            // then
            assertThat(response.getResponseCookies().get("JSESSIONID")).isNotNull();
        }

        @Test
        void withValidData_sessionEnablesOidcFlowWithoutLogin() throws Exception {
            // given
            var email = "oidc-" + UUID.randomUUID() + "@example.com";
            var requestBody = Map.of(
                    "householdName", "OIDC Haushalt",
                    "memberName", "OIDC Admin",
                    "email", email,
                    "password", "supersecret123"
            );
            var registration = restTestClient.post()
                    .uri("/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .exchange()
                    .expectStatus().isOk()
                    .returnResult();
            var sessionCookie = registration.getResponseCookies().getFirst("JSESSIONID");

            // when - use the session cookie from registration to start the PKCE flow
            var codeVerifier = generateCodeVerifier();
            var codeChallenge = generateCodeChallenge(codeVerifier);

            var authorizeResult = restTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/oauth2/authorize")
                            .queryParam("response_type", "code")
                            .queryParam("client_id", CLIENT_ID)
                            .queryParam("redirect_uri", REDIRECT_URI)
                            .queryParam("scope", "openid")
                            .queryParam("code_challenge", codeChallenge)
                            .queryParam("code_challenge_method", "S256")
                            .build())
                    .accept(MediaType.TEXT_HTML)
                    .cookie(sessionCookie.getName(), sessionCookie.getValue())
                    .exchange()
                    .expectStatus().is3xxRedirection()
                    .returnResult();

            // then - authorization code issued without prompting for login
            var location = authorizeResult.getResponseHeaders().getFirst(HttpHeaders.LOCATION);
            assertThat(location).startsWith(REDIRECT_URI).contains("code=");
        }

        @Test
        void withDuplicateEmail_returns409() {
            // given
            var email = "duplicate-" + UUID.randomUUID() + "@example.com";
            var requestBody = Map.of(
                    "householdName", "Erster Haushalt",
                    "memberName", "Max",
                    "email", email,
                    "password", "supersecret123"
            );
            restTestClient.post().uri("/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .exchange()
                    .expectStatus().isOk();

            // when
            var response = restTestClient.post()
                    .uri("/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .exchange();

            // then
            response.expectStatus().isEqualTo(409);
        }
    }

    @Nested
    class resolveInvite {

        @Test
        void withTokenFromRegistration_isAccessibleWithoutAuthentication() {
            // given
            var email = "invite-" + UUID.randomUUID() + "@example.com";
            var requestBody = Map.of(
                    "householdName", "Invite Haushalt",
                    "memberName", "Invite Admin",
                    "email", email,
                    "password", "supersecret123"
            );
            var registration = restTestClient.post()
                    .uri("/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .returnResult();
            var inviteToken = registration.getResponseBody().get("inviteToken");

            // when
            var response = restTestClient.get()
                    .uri("/v1/invite/{token}", inviteToken)
                    .exchange();

            // then
            response.expectStatus().isOk();
        }
    }

    private String generateCodeVerifier() {
        var bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateCodeChallenge(String codeVerifier) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        var bytes = digest.digest(codeVerifier.getBytes(UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}