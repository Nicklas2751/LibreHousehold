package eu.wiegandt.librehousehold.household.controller;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.auth.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.model.InviteEntity;
import eu.wiegandt.librehousehold.household.repository.InviteRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import eu.wiegandt.librehousehold.model.HouseholdSetupResponse;
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
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class LocalJoinRegistrationIT {

    private static final String REDIRECT_URI = "http://localhost:5173/callback";
    private static final String CLIENT_ID = "librehousehold-spa";

    @Autowired
    RestTestClient restTestClient;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    InviteRepository inviteRepository;

    @Nested
    class joinHousehold {

        @Test
        void withValidData_createsAccountAndMemberWithSharedId() {
            // given
            var token = registerHouseholdAndGetInviteToken();
            var email = "join-" + UUID.randomUUID() + "@example.com";
            var requestBody = Map.of(
                    "name", "Join Member",
                    "email", email,
                    "password", "supersecret123"
            );

            // when
            var response = restTestClient.post()
                    .uri("/v1/invite/{token}/join", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .returnResult();

            // then
            var memberId = UUID.fromString((String) response.getResponseBody().get("id"));
            assertThat(accountRepository.findById(memberId))
                    .hasValueSatisfying(a -> assertThat(a.email()).isEqualTo(email));
            assertThat(memberRepository.existsById(memberId)).isTrue();
        }

        @Test
        void withValidData_setsSessionCookieInResponse() {
            // given
            var token = registerHouseholdAndGetInviteToken();
            var requestBody = Map.of(
                    "name", "Join Member",
                    "email", "join-session-" + UUID.randomUUID() + "@example.com",
                    "password", "supersecret123"
            );

            // when
            var response = restTestClient.post()
                    .uri("/v1/invite/{token}/join", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .exchange()
                    .expectStatus().isCreated()
                    .returnResult();

            // then
            assertThat(response.getResponseCookies().get("JSESSIONID")).isNotNull();
        }

        @Test
        void withValidData_sessionEnablesOidcFlowWithoutLogin() throws Exception {
            // given
            var token = registerHouseholdAndGetInviteToken();
            var requestBody = Map.of(
                    "name", "Join Member",
                    "email", "join-oidc-" + UUID.randomUUID() + "@example.com",
                    "password", "supersecret123"
            );
            var joinResult = restTestClient.post()
                    .uri("/v1/invite/{token}/join", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .exchange()
                    .expectStatus().isCreated()
                    .returnResult();
            var sessionCookie = joinResult.getResponseCookies().getFirst("JSESSIONID");

            // when - use the session cookie from the join response to start the PKCE flow
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
            var email = "join-duplicate-" + UUID.randomUUID() + "@example.com";
            var firstToken = registerHouseholdAndGetInviteToken();
            var secondToken = registerHouseholdAndGetInviteToken();
            var requestBody = Map.of("name", "Join Member", "email", email, "password", "supersecret123");
            restTestClient.post().uri("/v1/invite/{token}/join", firstToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .exchange()
                    .expectStatus().isCreated();

            // when
            var response = restTestClient.post()
                    .uri("/v1/invite/{token}/join", secondToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .exchange();

            // then
            response.expectStatus().isEqualTo(409);
        }

        @Test
        void withExpiredToken_returns404() {
            // given
            var householdId = registerHouseholdAndGetHouseholdSetup().getHousehold().getId();
            var expiredInvite = inviteRepository.save(new InviteEntity(
                    null, householdId, UUID.randomUUID(), LocalDate.now().minusDays(1)));
            var requestBody = Map.of(
                    "name", "Join Member",
                    "email", "join-expired-" + UUID.randomUUID() + "@example.com",
                    "password", "supersecret123"
            );

            // when
            var response = restTestClient.post()
                    .uri("/v1/invite/{token}/join", expiredInvite.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .exchange();

            // then
            response.expectStatus().isNotFound();
        }
    }

    @Nested
    class joinHouseholdAuthenticated {

        @Test
        void withoutAuthentication_returns401() {
            // given
            var token = registerHouseholdAndGetInviteToken();
            var requestBody = Map.of("token", token.toString(), "memberName", "Social Member");

            // when
            var response = restTestClient.post()
                    .uri("/v1/household/join")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .exchange();

            // then
            response.expectStatus().isUnauthorized();
        }
    }

    private UUID registerHouseholdAndGetInviteToken() {
        return registerHouseholdAndGetHouseholdSetup().getInviteToken();
    }

    private HouseholdSetupResponse registerHouseholdAndGetHouseholdSetup() {
        var requestBody = Map.of(
                "householdName", "Join-Test Haushalt",
                "memberName", "Admin",
                "email", "admin-" + UUID.randomUUID() + "@example.com",
                "password", "supersecret123"
        );
        return restTestClient.post()
                .uri("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody(HouseholdSetupResponse.class)
                .returnResult()
                .getResponseBody();
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
