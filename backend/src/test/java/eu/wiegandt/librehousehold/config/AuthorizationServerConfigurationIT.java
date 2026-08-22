package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.mapper.MemberMapper;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import eu.wiegandt.librehousehold.household.service.AccountService;
import eu.wiegandt.librehousehold.model.CurrentUser;
import eu.wiegandt.librehousehold.model.UserPreferences;
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
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
 * Verifies the two {@link org.springframework.security.web.SecurityFilterChain} beans from
 * {@link SecurityConfig} (Authorization Server endpoints in one chain, everything else including
 * the backend's own {@code oauth2Login()} client in the other, see P1.4.4/DD-7). Uses a fixed port
 * instead of {@code RANDOM_PORT}: the backend is both Authorization Server and OAuth2 client of
 * itself in the same process, so the client-side token/userinfo/jwk endpoints must be reachable at
 * a base URL that is known at bean-creation time (see {@link SecurityConfig#clientRegistrationRepository}).
 *
 * <p>Uses {@link RestTestClient} (the Spring Boot 4.1-recommended fluent client, superseding
 * {@code TestRestTemplate} for this kind of test) bound to the real running server. Unlike
 * {@code TestRestTemplate}, {@code RestTestClient.bindToServer()} neither follows redirects
 * automatically nor maintains an automatic cookie jar across requests (every {@code exchange()}
 * call only carries the cookies explicitly attached to it): every step below reads the
 * {@code Location}/{@code Set-Cookie} response data explicitly and threads cookies to the next
 * request itself, which is exactly the manual, step-by-step control this test needs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=54327",
        "librehousehold.security.oauth2-authorization-server.issuer=http://localhost:54327",
        "librehousehold.security.oauth2-client.redirect-uri=http://localhost:54327/login/oauth2/code/spa-backend-client",
        "librehousehold.security.oauth2-client.client-secret=test-client-secret"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureRestTestClient
@ExtendWith(InstancioExtension.class)
class AuthorizationServerConfigurationIT {

    private static final String CLIENT_ID = "spa-backend-client";
    private static final String REDIRECT_URI = "http://localhost:54327/login/oauth2/code/spa-backend-client";
    private static final String RAW_PASSWORD = "correct horse battery staple";
    private static final Map<String, String> NO_COOKIES = Map.of();
    // Default of openapi.libreHousehold.base-path (see the generated *ApiController classes and
    // SecurityConfig#defaultSecurityFilterChain, which reads the same property) — not overridden
    // anywhere in application.yaml, so this is the actual path prefix in the running application.
    private static final String BASE_PATH = "/v1";

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private MemberMapper memberMapper;

    private UUID createdHouseholdId;

    @AfterEach
    void tearDown() {
        if (createdHouseholdId != null) {
            memberRepository.deleteByHouseholdId(createdHouseholdId);
            householdRepository.deleteById(createdHouseholdId);
        }
    }

    @Nested
    class oauth2Authorize {

        @Test
        void noSession_redirectsToLogin() {
            // given
            var authorizeUri = authorizeUri(REDIRECT_URI, codeChallenge("verifier"));

            // when
            var response = get(NO_COOKIES, URI.create(authorizeUri)).response();

            // then
            assertThat(response.getResponseHeaders().getLocation().getPath()).isEqualTo("/login");
        }

        @Test
        void afterLogin_redirectsToRegisteredRedirectUriWithCode() {
            // given
            var email = "authtest-" + UUID.randomUUID() + "@example.com";
            createMemberWithAccount(email, RAW_PASSWORD);
            var loginPage = requestLoginPageViaAuthorizationCodeFlow();
            var loginResult = submitLogin(loginPage.cookies(), loginPage.csrfToken(), email, RAW_PASSWORD);
            var cookiesAfterLogin = mergeCookies(loginPage.cookies(), loginResult);

            // when
            var response = get(cookiesAfterLogin, loginResult.getResponseHeaders().getLocation()).response();

            // then
            assertThat(response.getResponseHeaders().getLocation().toString()).startsWith(REDIRECT_URI + "?code=");
        }
    }

    @Nested
    class login {

        @Test
        void validAccountCredentials_redirectsBackToOriginalAuthorizeRequest() {
            // given
            var email = "authtest-" + UUID.randomUUID() + "@example.com";
            createMemberWithAccount(email, RAW_PASSWORD);
            var loginPage = requestLoginPageViaAuthorizationCodeFlow();

            // when
            var result = submitLogin(loginPage.cookies(), loginPage.csrfToken(), email, RAW_PASSWORD);

            // then
            assertThat(result.getResponseHeaders().getLocation().getPath()).isEqualTo("/oauth2/authorize");
        }
    }

    @Nested
    class loginCallback {

        @Test
        void validCode_responseBodyContainsNoToken() {
            // given
            var email = "authtest-" + UUID.randomUUID() + "@example.com";
            createMemberWithAccount(email, RAW_PASSWORD);

            // when
            var callbackResponse = performLoginAndFollowToCallback(email, RAW_PASSWORD).response();

            // then
            assertThat(Objects.requireNonNullElse(callbackResponse.getResponseBody(), ""))
                    .doesNotContain("access_token", "refresh_token");
        }
    }

    @Nested
    class getMe {

        @Test
        void withoutSession_returns401() {
            // when
            var response = restTestClient.get().uri(BASE_PATH + "/me")
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .returnResult(String.class);

            // then
            assertThat(response.getStatus().value()).isEqualTo(401);
        }

        @Test
        void afterSuccessfulLogin_returnsCurrentUser() {
            // given
            var email = "authtest-" + UUID.randomUUID() + "@example.com";
            var member = createMemberWithAccount(email, RAW_PASSWORD);
            var authenticatedCookies = performLoginAndFollowToCallback(email, RAW_PASSWORD).cookies();
            var expected = new CurrentUser(memberMapper.toMember(member), createdHouseholdId, new UserPreferences());

            // when
            var result = getWithCookies(authenticatedCookies, URI.create(BASE_PATH + "/me"))
                    .exchange().returnResult(CurrentUser.class);

            // then
            assertThat(result.getResponseBody()).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    /**
     * Nice-to-have companions to the earlier "does it redirect to /login" tests: proves that the
     * three endpoints that must be usable before any account exists are actually reachable at their
     * real, prefixed paths — the exact regression this test class previously missed (see P1.4.4
     * review): a permitAll() entry without the base-path prefix looks correct in isolation but never
     * matches a real, prefixed request.
     */
    @Nested
    class publicEndpoints {

        @Test
        void membersAvailability_noSession_returnsOk() {
            // given
            var uri = URI.create(BASE_PATH + "/members/availability?email=" + UUID.randomUUID() + "%40example.com");

            // when
            var response = get(NO_COOKIES, uri).response();

            // then
            assertThat(response.getStatus().value()).isEqualTo(200);
        }

        @Test
        void householdSetup_noSession_isReachable() {
            // given — GET instead of the real POST sidesteps CSRF (POST /household/setup without a
            // token is a separate, already-known limitation owned by P1.5.2, not this permitAll fix)
            // while still proving the security layer lets the request through to the controller.
            var uri = URI.create(BASE_PATH + "/household/setup");

            // when
            var response = get(NO_COOKIES, uri).response();

            // then
            assertThat(response.getStatus().value()).isEqualTo(405);
        }

        @Test
        void resolveInvite_noSession_isReachable() {
            // given — an invite token that does not exist reaches the delegate, which reports 404;
            // this also proves /error itself must stay publicly reachable, since Spring MVC forwards
            // there internally for any non-2xx response from a permitAll endpoint.
            var uri = URI.create(BASE_PATH + "/invite/" + UUID.randomUUID());

            // when
            var response = get(NO_COOKIES, uri).response();

            // then
            assertThat(response.getStatus().value()).isEqualTo(404);
        }
    }

    /**
     * Drives the authorization-code flow, starting from Spring's own client-side redirect entry
     * point ({@code /oauth2/authorization/spa-backend-client}), up to and including fetching the
     * {@code /login} page (so PKCE parameters and {@code state} are generated by Spring itself,
     * exactly as they would be for a real SPA navigation, see DD-7).
     */
    private LoginPage requestLoginPageViaAuthorizationCodeFlow() {
        var authorizationRequest = get(NO_COOKIES, URI.create("/oauth2/authorization/" + CLIENT_ID));
        var authorizeRequest = get(authorizationRequest.cookies(), authorizationRequest.response().getResponseHeaders().getLocation());
        var loginPageRequest = get(authorizeRequest.cookies(), authorizeRequest.response().getResponseHeaders().getLocation());
        var csrfToken = extractCsrfToken(loginPageRequest.response().getResponseBody());
        return new LoginPage(loginPageRequest.cookies(), csrfToken);
    }

    /**
     * Drives a full, real login (form submission with valid credentials, redirect back to
     * {@code /oauth2/authorize}, authorization code issuance, and finally the client-side callback
     * that exchanges the code for tokens and enriches the {@code OidcUser} via
     * {@code AccountOidcUserService}). Returns the last step, whose cookies carry the now fully
     * authenticated session.
     */
    private HttpStep performLoginAndFollowToCallback(String email, String rawPassword) {
        var loginPage = requestLoginPageViaAuthorizationCodeFlow();
        var loginResult = submitLogin(loginPage.cookies(), loginPage.csrfToken(), email, rawPassword);
        var cookiesAfterLogin = mergeCookies(loginPage.cookies(), loginResult);
        var authorizeAgain = get(cookiesAfterLogin, loginResult.getResponseHeaders().getLocation());
        return get(authorizeAgain.cookies(), authorizeAgain.response().getResponseHeaders().getLocation());
    }

    private HttpStep get(Map<String, String> cookies, URI uri) {
        var response = getWithCookies(cookies, uri).exchange().returnResult(String.class);
        return new HttpStep(mergeCookies(cookies, response), response);
    }

    private RestTestClient.RequestHeadersSpec<?> getWithCookies(Map<String, String> cookies, URI uri) {
        return applyCookies(restTestClient.get().uri(uri), cookies);
    }

    private EntityExchangeResult<Void> submitLogin(Map<String, String> cookies, String csrfToken, String email, String rawPassword) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("username", email);
        form.add("password", rawPassword);
        form.add("_csrf", csrfToken);
        var spec = restTestClient.post().uri("/login").contentType(MediaType.APPLICATION_FORM_URLENCODED);
        for (var cookie : cookies.entrySet()) {
            spec = spec.cookie(cookie.getKey(), cookie.getValue());
        }
        return spec.body(form).exchange().returnResult(Void.class);
    }

    private RestTestClient.RequestHeadersSpec<?> applyCookies(RestTestClient.RequestHeadersSpec<?> spec, Map<String, String> cookies) {
        for (var cookie : cookies.entrySet()) {
            spec = spec.cookie(cookie.getKey(), cookie.getValue());
        }
        return spec;
    }

    private MemberEntity createMemberWithAccount(String email, String rawPassword) {
        var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
        createdHouseholdId = household.id();
        var member = memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), household.id())
                .set(field(MemberEntity::email), email)
                .create());
        accountService.createAccount(member.getId(), rawPassword);
        return member;
    }

    private String authorizeUri(String redirectUri, String codeChallenge) {
        return UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", CLIENT_ID)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .queryParam("scope", "openid")
                .build()
                .encode()
                .toUriString();
    }

    private String codeChallenge(String codeVerifier) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Merges {@code Set-Cookie} response cookies into the cumulative cookie map used for the next
     * step of a manually driven multi-request browser flow.
     */
    private Map<String, String> mergeCookies(Map<String, String> existingCookies, ExchangeResult response) {
        var merged = new LinkedHashMap<>(existingCookies);
        response.getResponseCookies().values().stream()
                .flatMap(List::stream)
                .forEach(cookie -> merged.put(cookie.getName(), cookie.getValue()));
        return merged;
    }

    private String extractCsrfToken(String loginPageHtml) {
        var csrfInputStart = loginPageHtml.indexOf("name=\"_csrf\"");
        var inputTag = loginPageHtml.substring(csrfInputStart, loginPageHtml.indexOf('>', csrfInputStart));
        var matcher = Pattern.compile("value=\"([^\"]*)\"").matcher(inputTag);
        matcher.find();
        return matcher.group(1);
    }

    private record HttpStep(Map<String, String> cookies, EntityExchangeResult<String> response) {
    }

    private record LoginPage(Map<String, String> cookies, String csrfToken) {
    }
}
