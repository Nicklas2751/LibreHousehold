package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.DexTestcontainersConfiguration;
import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import eu.wiegandt.librehousehold.household.service.AccountService;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;
import wf.garnier.testcontainers.dexidp.DexContainer;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;

/**
 * Drives the real social-login redirect flow against a Dex container (the {@code dev-provider}
 * registration from {@link DexTestcontainersConfiguration}) on a random port: real session-ID
 * changes, real {@code Set-Cookie} headers and real redirects to Dex (see ADR-013).
 *
 * <p>Calls to Dex itself go through a plain {@link HttpClient} with its own cookie store, because
 * they target a different origin than the backend, whose cookies are threaded through manually.
 * Each test registers its own Dex user and re-registers the Dex client with this test server's
 * callback URI (the configuration's default is the Vite dev proxy origin).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, DexTestcontainersConfiguration.class})
@AutoConfigureRestTestClient
@ExtendWith(InstancioExtension.class)
// The same Dex provider under a second registration ID, as if a self-hoster renamed the registration
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.renamed-provider.provider=dev-provider",
        "spring.security.oauth2.client.registration.renamed-provider.client-id=" + DexTestcontainersConfiguration.CLIENT_ID,
        "spring.security.oauth2.client.registration.renamed-provider.client-secret=" + DexTestcontainersConfiguration.CLIENT_SECRET,
        "spring.security.oauth2.client.registration.renamed-provider.scope=openid,profile,email"})
class SocialLoginFilterChainIT {

    private static final String REGISTRATION_ID = "dev-provider";
    private static final String RENAMED_REGISTRATION_ID = "renamed-provider";
    private static final String CALLBACK_PATH = "/login/oauth2/code/" + REGISTRATION_ID;
    private static final String AUTHORIZATION_PATH = "/oauth2/authorization/" + REGISTRATION_ID;
    private static final String DEX_PASSWORD = "dex-password";
    private static final String BASE_PATH = "/v1";
    private static final String SESSION_COOKIE_NAME = "JSESSIONID";
    private static final String XSRF_TOKEN_COOKIE_NAME = "XSRF-TOKEN";
    private static final String XSRF_TOKEN_HEADER_NAME = "X-XSRF-TOKEN";
    private static final Map<String, String> NO_COOKIES = Map.of();
    private static final int MAX_REDIRECTS = 10;
    private static final Pattern FORM_ACTION = Pattern.compile("<form[^>]*action=\"([^\"]*)\"");

    @LocalServerPort
    private int port;

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private DexContainer dexContainer;

    @Autowired
    private AccountService accountService;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private HouseholdEntity createdHousehold;

    @AfterEach
    void tearDown() {
        if (createdHousehold != null) {
            memberRepository.deleteByHouseholdId(createdHousehold.id());
            householdRepository.deleteById(createdHousehold.id());
        }
    }

    @Nested
    class login {

        @Test
        void linkedIdentity_redirectsToDashboard() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, dexRedirectUri()));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);
            var member = createMember(dexUser.email());
            accountService.createFederatedAccount(member.getId());
            accountService.linkExternalIdentity(member.getId(), dexContainer.getIssuerUri(), dexSubjectOf(dexUser));

            // when
            var callback = loginViaDex(NO_COOKIES, dexUser);

            // then
            assertThat(callback.response().getResponseHeaders().getLocation()).hasPath("/app/dashboard").hasNoQuery();
        }

        @Test
        void linkedIdentity_meReturns200() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, dexRedirectUri()));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);
            var member = createMember(dexUser.email());
            accountService.createFederatedAccount(member.getId());
            accountService.linkExternalIdentity(member.getId(), dexContainer.getIssuerUri(), dexSubjectOf(dexUser));
            var callback = loginViaDex(NO_COOKIES, dexUser);

            // when
            var meResponse = get(callback.cookies(), URI.create(BASE_PATH + "/me")).response();

            // then
            assertThat(meResponse.getStatus().value()).isEqualTo(200);
        }

        @Test
        void linkedIdentity_oldSessionIdRejected() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, dexRedirectUri()));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);
            var member = createMember(dexUser.email());
            accountService.createFederatedAccount(member.getId());
            accountService.linkExternalIdentity(member.getId(), dexContainer.getIssuerUri(), dexSubjectOf(dexUser));
            var anonymousSession = storeSocialLoginContext("{\"context\":\"LOGIN\"}");
            var callback = loginViaDex(anonymousSession, dexUser);
            var oldSessionCookies = new LinkedHashMap<>(callback.cookies());
            oldSessionCookies.put(SESSION_COOKIE_NAME, anonymousSession.get(SESSION_COOKIE_NAME));

            // when
            var meResponse = get(oldSessionCookies, URI.create(BASE_PATH + "/me")).response();

            // then
            assertThat(meResponse.getStatus().value()).isEqualTo(401);
        }

        @Test
        void noLinkedIdentity_rejectsWithNoAccountReason() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, dexRedirectUri()));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);

            // when
            var callback = loginViaDex(NO_COOKIES, dexUser);

            // then
            assertThat(callback.response().getResponseHeaders().getLocation())
                    .hasPath("/login").hasQuery("error&reason=social-login-no-account");
        }

        /**
         * ADR-018: only an explicitly linked identity counts — a password account with the same
         * e-mail address as the external account is never linked automatically.
         */
        @Test
        void emailMatchesPasswordAccount_rejectsWithNoAccountReason() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, dexRedirectUri()));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);
            var member = createMember(dexUser.email());
            accountService.createAccount(member.getId(), "correct horse battery staple");
            accountService.markEmailVerified(member.getId());

            // when
            var callback = loginViaDex(NO_COOKIES, dexUser);

            // then
            assertThat(callback.response().getResponseHeaders().getLocation())
                    .hasPath("/login").hasQuery("error&reason=social-login-no-account");
        }

        /**
         * ADR-018: the identity is keyed by the provider's issuer, so another provider issuing the
         * same subject never reaches the linked account.
         */
        @Test
        void sameSubjectDifferentIssuer_notLinked() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, dexRedirectUri()));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);
            var member = createMember(dexUser.email());
            accountService.createFederatedAccount(member.getId());
            accountService.linkExternalIdentity(member.getId(), "https://other-issuer.example.com", dexSubjectOf(dexUser));

            // when
            var callback = loginViaDex(NO_COOKIES, dexUser);

            // then
            assertThat(callback.response().getResponseHeaders().getLocation())
                    .hasPath("/login").hasQuery("error&reason=social-login-no-account");
        }

        /**
         * ADR-018: the registration ID is the self-hoster's choice and no part of the identity, so
         * renaming the registration keeps the account linked.
         */
        @Test
        void renamedRegistrationId_stillLinked() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, dexRedirectUri()));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);
            var member = createMember(dexUser.email());
            accountService.createFederatedAccount(member.getId());
            accountService.linkExternalIdentity(member.getId(), dexContainer.getIssuerUri(), dexSubjectOf(dexUser));
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, dexRedirectUri(RENAMED_REGISTRATION_ID)));

            // when
            var callback = loginViaDex(NO_COOKIES, dexUser, RENAMED_REGISTRATION_ID);

            // then
            assertThat(callback.response().getResponseHeaders().getLocation()).hasPath("/app/dashboard").hasNoQuery();
        }

        /**
         * The user cancelled at the provider or the provider refused: Dex is not involved, since the
         * provider's error redirect is simulated directly against the backend's callback.
         */
        @Test
        void providerDeniedAccess_rejectsWithSocialLoginFailedReason() {
            // given
            var authorizationRequest = get(NO_COOKIES, URI.create(AUTHORIZATION_PATH));
            var state = UriComponentsBuilder.fromUri(authorizationRequest.response().getResponseHeaders().getLocation())
                    .build().getQueryParams().getFirst("state");

            // when
            var callback = get(authorizationRequest.cookies(),
                    URI.create(CALLBACK_PATH + "?error=access_denied&state=" + state));

            // then
            assertThat(callback.response().getResponseHeaders().getLocation())
                    .hasPath("/login").hasQuery("error&reason=social-login-failed");
        }
    }

    @Nested
    class callback {

        @Test
        void linkedIdentity_responseBodyContainsNoToken() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, dexRedirectUri()));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);
            var member = createMember(dexUser.email());
            accountService.createFederatedAccount(member.getId());
            accountService.linkExternalIdentity(member.getId(), dexContainer.getIssuerUri(), dexSubjectOf(dexUser));

            // when
            var callback = loginViaDex(NO_COOKIES, dexUser);

            // then
            assertThat(Objects.requireNonNullElse(callback.response().getResponseBody(), ""))
                    .doesNotContain("access_token", "refresh_token", "id_token");
        }

        /**
         * The external authentication only starts a registration; it must not leave the session
         * authenticated before household setup or invite join created the member.
         */
        @Test
        void verifiedEmailInSetupContext_businessEndpointWithSameSession_returns401() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, dexRedirectUri()));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);
            var callback = loginViaDex(storeSocialLoginContext("{\"context\":\"SETUP\"}"), dexUser);

            // when
            var response = get(callback.cookies(), URI.create(BASE_PATH + "/household/" + UUID.randomUUID() + "/tasks")).response();

            // then
            assertThat(response.getStatus().value()).isEqualTo(401);
        }
    }

    @Test
    void authorizationRedirect_containsCodeChallengeStateAndNonce() {
        // when
        var authorizationRequest = get(NO_COOKIES, URI.create(AUTHORIZATION_PATH));

        // then
        assertThat(UriComponentsBuilder.fromUri(authorizationRequest.response().getResponseHeaders().getLocation())
                .build().getQueryParams())
                .containsKeys("code_challenge", "state", "nonce")
                .containsEntry("code_challenge_method", List.of("S256"));
    }

    /**
     * Starts the social login at the backend, logs in at Dex and returns the backend's response to
     * Dex's redirect back to the callback, whose cookies carry the resulting session.
     */
    private HttpStep loginViaDex(Map<String, String> cookies, DexContainer.User dexUser) throws IOException, InterruptedException {
        return loginViaDex(cookies, dexUser, REGISTRATION_ID);
    }

    private HttpStep loginViaDex(Map<String, String> cookies, DexContainer.User dexUser, String registrationId)
            throws IOException, InterruptedException {
        var authorizationRequest = get(cookies, URI.create("/oauth2/authorization/" + registrationId));
        var callbackUri = loginAtDex(authorizationRequest.response().getResponseHeaders().getLocation(), dexUser,
                dexRedirectUri(registrationId));
        return get(authorizationRequest.cookies(), URI.create(callbackUri.getRawPath() + "?" + callbackUri.getRawQuery()));
    }

    /**
     * Dex's {@code sub} is opaque (derived from its internal user ID), so it is obtained black-box
     * via a separate authorization-code flow directly against Dex, decoding the issued ID token.
     */
    private String dexSubjectOf(DexContainer.User dexUser) throws IOException, InterruptedException {
        var issuer = dexContainer.getIssuerUri();
        var authorizationUri = UriComponentsBuilder.fromUriString(issuer + "/auth")
                .queryParam("client_id", DexTestcontainersConfiguration.CLIENT_ID)
                .queryParam("redirect_uri", dexRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid")
                .queryParam("state", UUID.randomUUID())
                .build().encode().toUri();
        var code = UriComponentsBuilder.fromUri(loginAtDex(authorizationUri, dexUser, dexRedirectUri())).build()
                .getQueryParams().getFirst("code");
        var credentials = Base64.getEncoder().encodeToString(
                (DexTestcontainersConfiguration.CLIENT_ID + ":" + DexTestcontainersConfiguration.CLIENT_SECRET)
                        .getBytes(StandardCharsets.UTF_8));
        var tokenRequest = HttpRequest.newBuilder(URI.create(issuer + "/token"))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(formBody(Map.of(
                        "grant_type", "authorization_code",
                        "code", code,
                        "redirect_uri", dexRedirectUri()))))
                .build();
        try (var httpClient = HttpClient.newHttpClient()) {
            var tokenResponse = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            var idToken = objectMapper.readTree(tokenResponse.body()).get("id_token").asString();
            var payload = new String(Base64.getUrlDecoder().decode(idToken.split("\\.")[1]), StandardCharsets.UTF_8);
            return objectMapper.readTree(payload).get("sub").asString();
        }
    }

    /**
     * Follows Dex's redirects to its password login form, submits the user's credentials and
     * follows the redirects until Dex sends the browser back to the given redirect URI.
     */
    private URI loginAtDex(URI authorizationUri, DexContainer.User dexUser, String redirectUri)
            throws IOException, InterruptedException {
        try (var dexBrowser = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .cookieHandler(new CookieManager())
                .build()) {
            var loginForm = followRedirectsUntilPage(dexBrowser, authorizationUri);
            var formAction = loginForm.uri().resolve(extractFormAction(loginForm.body()));
            var loginRequest = HttpRequest.newBuilder(formAction)
                    .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(formBody(Map.of(
                            "login", dexUser.email(),
                            "password", dexUser.clearTextPassword()))))
                    .build();
            var location = dexBrowser.send(loginRequest, HttpResponse.BodyHandlers.ofString()).headers()
                    .firstValue("Location").map(formAction::resolve).orElseThrow();
            while (!location.toString().startsWith(redirectUri)) {
                var redirect = dexBrowser.send(HttpRequest.newBuilder(location).build(), HttpResponse.BodyHandlers.ofString());
                location = location.resolve(redirect.headers().firstValue("Location").orElseThrow());
            }
            return location;
        }
    }

    private HttpResponse<String> followRedirectsUntilPage(HttpClient dexBrowser, URI uri) throws IOException, InterruptedException {
        var response = dexBrowser.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
        // Bounded, so a misconfigured provider (e.g. a redirect loop on /login) fails the test instead of hanging it
        for (var redirects = 0; response.headers().firstValue("Location").isPresent(); redirects++) {
            var next = response.uri().resolve(response.headers().firstValue("Location").get());
            if (redirects == MAX_REDIRECTS) {
                throw new IllegalStateException("Too many redirects, last to " + next);
            }
            response = dexBrowser.send(HttpRequest.newBuilder(next).build(), HttpResponse.BodyHandlers.ofString());
        }
        return response;
    }

    private String extractFormAction(String html) {
        var matcher = FORM_ACTION.matcher(html);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).replace("&amp;", "&");
    }

    private String formBody(Map<String, String> fields) {
        var joiner = new StringBuilder();
        for (var entry : fields.entrySet()) {
            if (!joiner.isEmpty()) {
                joiner.append('&');
            }
            joiner.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return joiner.toString();
    }

    private MemberEntity createMember(String email) {
        createdHousehold = householdRepository.save(Instancio.create(HouseholdEntity.class));
        return memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), createdHousehold.id())
                .set(field(MemberEntity::email), email)
                .create());
    }

    // Boot's default redirect URI {baseUrl}/login/oauth2/code/{registrationId}, as seen from this server
    private String dexRedirectUri() {
        return dexRedirectUri(REGISTRATION_ID);
    }

    private String dexRedirectUri(String registrationId) {
        return "http://localhost:" + port + "/login/oauth2/code/" + registrationId;
    }

    private Map<String, String> storeSocialLoginContext(String context) {
        var csrfPreflight = get(NO_COOKIES, URI.create(BASE_PATH + "/auth/discover"));
        return post(csrfPreflight.cookies(), BASE_PATH + "/auth/social-context", context).cookies();
    }

    private HttpStep get(Map<String, String> cookies, URI uri) {
        // Like the SPA's fetch calls; a browser navigation (text/html) would be redirected to the login page instead of a 401
        var response = applyCookies(restTestClient.get().uri(uri), cookies)
                .accept(MediaType.APPLICATION_JSON)
                .exchange().returnResult(String.class);
        return new HttpStep(mergeCookies(cookies, response), response);
    }

    private HttpStep post(Map<String, String> cookies, String uri, String jsonBody) {
        var response = applyCookies(restTestClient.post().uri(uri), cookies)
                .header(XSRF_TOKEN_HEADER_NAME, cookies.get(XSRF_TOKEN_COOKIE_NAME))
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBody)
                .exchange().returnResult(String.class);
        return new HttpStep(mergeCookies(cookies, response), response);
    }

    private <S extends RestTestClient.RequestHeadersSpec<?>> S applyCookies(S spec, Map<String, String> cookies) {
        for (var cookie : cookies.entrySet()) {
            spec.cookie(cookie.getKey(), cookie.getValue());
        }
        return spec;
    }

    private Map<String, String> mergeCookies(Map<String, String> existingCookies, ExchangeResult response) {
        var merged = new LinkedHashMap<>(existingCookies);
        response.getResponseCookies().values().stream()
                .flatMap(List::stream)
                .forEach(cookie -> merged.put(cookie.getName(), cookie.getValue()));
        return merged;
    }

    private record HttpStep(Map<String, String> cookies, EntityExchangeResult<String> response) {
    }
}
