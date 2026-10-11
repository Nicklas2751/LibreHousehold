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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.TestSocketUtils;
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
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;

/**
 * Drives the real social-login redirect flow against a Dex container (the {@code dev-provider}
 * registration from {@link DexTestcontainersConfiguration}). Uses a port picked before startup for
 * the same reason as {@link AuthorizationServerConfigurationIT}: the embedded Authorization Server's
 * issuer, which the SPA continuation after the external login needs, must contain the actual port.
 *
 * <p>Calls to Dex itself go through a plain {@link HttpClient} with its own cookie store, because
 * they target a different origin than the backend, whose cookies are threaded through manually.
 * Each test registers its own Dex user and re-registers the Dex client with this test server's
 * callback URI (the configuration's default is the Vite dev proxy origin).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "librehousehold.security.oauth2-client.client-secret=test-client-secret"
})
@Import({TestcontainersConfiguration.class, DexTestcontainersConfiguration.class})
@AutoConfigureRestTestClient
@ExtendWith(InstancioExtension.class)
class SocialLoginFilterChainIT {

    private static final int SERVER_PORT = TestSocketUtils.findAvailableTcpPort();
    private static final String SERVER_BASE_URL = "http://localhost:" + SERVER_PORT;

    private static final String PROVIDER_ID = "dev-provider";
    private static final String DEX_REDIRECT_URI = SERVER_BASE_URL + "/login/oauth2/code/" + PROVIDER_ID;
    private static final String DEX_PASSWORD = "dex-password";
    private static final String BASE_PATH = "/v1";
    private static final String XSRF_TOKEN_COOKIE_NAME = "XSRF-TOKEN";
    private static final String XSRF_TOKEN_HEADER_NAME = "X-XSRF-TOKEN";
    private static final Map<String, String> NO_COOKIES = Map.of();
    private static final Pattern FORM_ACTION = Pattern.compile("<form[^>]*action=\"([^\"]*)\"");

    @DynamicPropertySource
    static void serverPortProperties(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> SERVER_PORT);
        registry.add("librehousehold.security.oauth2-authorization-server.issuer", () -> SERVER_BASE_URL);
        registry.add("librehousehold.security.oauth2-client.redirect-uri",
                () -> SERVER_BASE_URL + "/login/oauth2/code/spa-backend-client");
    }

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
        void linkedIdentity_establishesAuthenticatedSession() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, DEX_REDIRECT_URI));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);
            var member = createMember(dexUser.email());
            accountService.createFederatedAccount(member.getId());
            accountService.linkExternalIdentity(member.getId(), PROVIDER_ID, dexSubjectOf(dexUser));

            // when
            var callback = loginViaDex(NO_COOKIES, dexUser);
            var spaAuthorization = get(callback.cookies(), callback.response().getResponseHeaders().getLocation());
            var authorize = get(spaAuthorization.cookies(), spaAuthorization.response().getResponseHeaders().getLocation());
            var spaCallback = get(authorize.cookies(), authorize.response().getResponseHeaders().getLocation());
            var meResponse = get(spaCallback.cookies(), URI.create(BASE_PATH + "/me")).response();

            // then
            assertThat(meResponse.getStatus().value()).isEqualTo(200);
        }

        @Test
        void noLinkedIdentity_rejectsWithNoAccountReason() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, DEX_REDIRECT_URI));
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
                    DexTestcontainersConfiguration.CLIENT_SECRET, DEX_REDIRECT_URI));
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

        @Test
        void setupContextStored_rejectsWithNoAccountReason() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, DEX_REDIRECT_URI));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);
            var member = createMember(dexUser.email());
            accountService.createFederatedAccount(member.getId());
            accountService.linkExternalIdentity(member.getId(), PROVIDER_ID, dexSubjectOf(dexUser));
            var csrfPreflight = get(NO_COOKIES, URI.create(BASE_PATH + "/members/availability?email=social%40example.com"));
            var storeContextResponse = applyCookies(restTestClient.post().uri(BASE_PATH + "/auth/social-context"), csrfPreflight.cookies())
                    .header(XSRF_TOKEN_HEADER_NAME, csrfPreflight.cookies().get(XSRF_TOKEN_COOKIE_NAME))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"context\":\"SETUP\"}")
                    .exchange().returnResult(String.class);
            var cookiesWithContext = mergeCookies(csrfPreflight.cookies(), storeContextResponse);

            // when
            var callback = loginViaDex(cookiesWithContext, dexUser);

            // then
            assertThat(callback.response().getResponseHeaders().getLocation())
                    .hasPath("/login").hasQuery("error&reason=social-login-no-account");
        }
    }

    /**
     * Starts the social login at the backend, logs in at Dex and returns the backend's response to
     * Dex's redirect back to the callback, whose cookies carry the resulting session.
     */
    private HttpStep loginViaDex(Map<String, String> cookies, DexContainer.User dexUser) throws IOException, InterruptedException {
        var authorizationRequest = get(cookies, URI.create("/oauth2/authorization/" + PROVIDER_ID));
        var callbackUri = loginAtDex(authorizationRequest.response().getResponseHeaders().getLocation(), dexUser);
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
                .queryParam("redirect_uri", DEX_REDIRECT_URI)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid")
                .queryParam("state", UUID.randomUUID())
                .build().encode().toUri();
        var code = UriComponentsBuilder.fromUri(loginAtDex(authorizationUri, dexUser)).build()
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
                        "redirect_uri", DEX_REDIRECT_URI))))
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
     * follows the redirects until Dex sends the browser back to {@link #DEX_REDIRECT_URI}.
     */
    private URI loginAtDex(URI authorizationUri, DexContainer.User dexUser) throws IOException, InterruptedException {
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
            while (!location.toString().startsWith(DEX_REDIRECT_URI)) {
                var redirect = dexBrowser.send(HttpRequest.newBuilder(location).build(), HttpResponse.BodyHandlers.ofString());
                location = location.resolve(redirect.headers().firstValue("Location").orElseThrow());
            }
            return location;
        }
    }

    private HttpResponse<String> followRedirectsUntilPage(HttpClient dexBrowser, URI uri) throws IOException, InterruptedException {
        var response = dexBrowser.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
        while (response.headers().firstValue("Location").isPresent()) {
            var next = response.uri().resolve(response.headers().firstValue("Location").get());
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

    private HttpStep get(Map<String, String> cookies, URI uri) {
        var response = applyCookies(restTestClient.get().uri(uri), cookies).exchange().returnResult(String.class);
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
