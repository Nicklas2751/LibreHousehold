package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.DexTestcontainersConfiguration;
import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.model.AccountIdentityEntity;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.InviteEntity;
import eu.wiegandt.librehousehold.household.repository.AccountIdentityRepository;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.InviteRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives household setup and invite join via social login end to end: storing the context, the
 * real login at a Dex container, the confirmation via {@code GET /auth/social-registration}, the
 * registration request without {@code localRegistration}, which signs the new member in. Runs on a
 * random port and re-registers the Dex client per test for the reasons given in
 * {@link SocialLoginFilterChainIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, DexTestcontainersConfiguration.class})
@AutoConfigureRestTestClient
@ExtendWith(InstancioExtension.class)
class SocialSetupFlowIT {

    private static final String REGISTRATION_ID = "dev-provider";
    private static final String CALLBACK_PATH = "/login/oauth2/code/" + REGISTRATION_ID;
    private static final String DEX_PASSWORD = "dex-password";
    private static final String BASE_PATH = "/v1";
    private static final String XSRF_TOKEN_COOKIE_NAME = "XSRF-TOKEN";
    private static final String XSRF_TOKEN_HEADER_NAME = "X-XSRF-TOKEN";
    private static final String SESSION_COOKIE_NAME = "JSESSIONID";
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
    private HouseholdRepository householdRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private InviteRepository inviteRepository;

    @Autowired
    private AccountIdentityRepository accountIdentityRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> createdHouseholdIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (var householdId : createdHouseholdIds) {
            inviteRepository.deleteByHouseholdId(householdId);
            memberRepository.deleteByHouseholdId(householdId);
            householdRepository.deleteById(householdId);
        }
    }

    @Nested
    class householdSetup {

        @Test
        void dexLoginWithSetupContext_redirectsBackToSetupPage() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, dexRedirectUri()));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);
            var cookiesWithContext = storeSocialLoginContext("{\"context\":\"SETUP\"}");

            // when
            var callback = loginViaDex(cookiesWithContext, dexUser);

            // then
            assertThat(callback.response().getResponseHeaders().getLocation()).hasPath("/setup").hasQuery("social");
        }

        @Test
        void dexLoginWithSetupContext_pendingRegistrationCarriesDexIdentity() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, dexRedirectUri()));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);
            var callback = loginViaDex(storeSocialLoginContext("{\"context\":\"SETUP\"}"), dexUser);
            var expected = Map.of("name", dexUser.username(), "email", dexUser.email());

            // when
            var pendingRegistration = get(callback.cookies(), URI.create(BASE_PATH + "/auth/social-registration")).response();

            // then
            assertThat(objectMapper.readValue(pendingRegistration.getResponseBody(), Map.class)).isEqualTo(expected);
        }

        @Test
        void getPendingSocialRegistration_otherClientWithoutCookies_notFound() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, dexRedirectUri()));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);
            loginViaDex(storeSocialLoginContext("{\"context\":\"SETUP\"}"), dexUser);

            // when
            var pendingRegistration = get(NO_COOKIES, URI.create(BASE_PATH + "/auth/social-registration")).response();

            // then
            assertThat(pendingRegistration.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void getPendingSocialRegistration_otherClientWithOwnSession_notFound() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, dexRedirectUri()));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);
            var callback = loginViaDex(storeSocialLoginContext("{\"context\":\"SETUP\"}"), dexUser);
            var otherClientCookies = storeSocialLoginContext("{\"context\":\"SETUP\"}");
            // Guards against a vacuous pass: the other client must really send a session of its own.
            assertThat(otherClientCookies.get(SESSION_COOKIE_NAME))
                    .isNotNull()
                    .isNotEqualTo(callback.cookies().get(SESSION_COOKIE_NAME));

            // when
            var pendingRegistration = get(otherClientCookies, URI.create(BASE_PATH + "/auth/social-registration")).response();

            // then
            assertThat(pendingRegistration.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void setupWithoutLocalRegistration_signsInNewAdminWithDexEmail() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, dexRedirectUri()));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);
            var householdId = UUID.randomUUID();
            createdHouseholdIds.add(householdId);
            var callback = loginViaDex(storeSocialLoginContext("{\"context\":\"SETUP\"}"), dexUser);
            var pendingRegistration = get(callback.cookies(), URI.create(BASE_PATH + "/auth/social-registration"));
            var setupBody = """
                    {"household": {"id": "%s", "name": "Social Household"},
                     "member": {"id": "%s", "name": "Requested Name", "email": "ignored@example.com"}}
                    """.formatted(householdId, UUID.randomUUID());
            var expected = new SignedInMember(dexUser.email(), "Requested Name", householdId.toString(), true);

            // when
            var setup = post(pendingRegistration.cookies(), BASE_PATH + "/household/setup", setupBody);
            var meResponse = get(setup.cookies(), URI.create(BASE_PATH + "/me")).response();

            // then
            assertThat(toSignedInMember(meResponse)).isEqualTo(expected);
        }

        @Test
        void setupWithoutLocalRegistration_meReportsSocialLoginOnly() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, dexRedirectUri()));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);
            var householdId = UUID.randomUUID();
            createdHouseholdIds.add(householdId);
            var callback = loginViaDex(storeSocialLoginContext("{\"context\":\"SETUP\"}"), dexUser);
            var pendingRegistration = get(callback.cookies(), URI.create(BASE_PATH + "/auth/social-registration"));
            var setupBody = """
                    {"household": {"id": "%s", "name": "Social Household"},
                     "member": {"id": "%s", "name": "Requested Name", "email": "ignored@example.com"}}
                    """.formatted(householdId, UUID.randomUUID());

            // when
            var setup = post(pendingRegistration.cookies(), BASE_PATH + "/household/setup", setupBody);
            var meResponse = get(setup.cookies(), URI.create(BASE_PATH + "/me")).response();

            // then
            assertThat(objectMapper.readTree(meResponse.getResponseBody()).get("socialLoginOnly").asBoolean()).isTrue();
        }

        @Test
        void setupWithoutLocalRegistration_meReturns200() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, dexRedirectUri()));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);
            var householdId = UUID.randomUUID();
            createdHouseholdIds.add(householdId);
            var callback = loginViaDex(storeSocialLoginContext("{\"context\":\"SETUP\"}"), dexUser);
            var setupBody = """
                    {"household": {"id": "%s", "name": "Social Household"},
                     "member": {"id": "%s", "name": "Requested Name", "email": "ignored@example.com"}}
                    """.formatted(householdId, UUID.randomUUID());
            var setup = post(callback.cookies(), BASE_PATH + "/household/setup", setupBody);

            // when
            var meResponse = get(setup.cookies(), URI.create(BASE_PATH + "/me")).response();

            // then
            assertThat(meResponse.getStatus().value()).isEqualTo(200);
        }

        /**
         * ADR-018: the identity is linked by the provider's issuer, not by the registration ID.
         */
        @Test
        void setupWithoutLocalRegistration_storesIssuerAsProviderId() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, dexRedirectUri()));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);
            var householdId = UUID.randomUUID();
            createdHouseholdIds.add(householdId);
            var memberId = UUID.randomUUID();
            var callback = loginViaDex(storeSocialLoginContext("{\"context\":\"SETUP\"}"), dexUser);
            var setupBody = """
                    {"household": {"id": "%s", "name": "Social Household"},
                     "member": {"id": "%s", "name": "Requested Name", "email": "ignored@example.com"}}
                    """.formatted(householdId, memberId);

            // when
            post(callback.cookies(), BASE_PATH + "/household/setup", setupBody);

            // then
            assertThat(accountIdentityRepository.findAll())
                    .filteredOn(identity -> identity.memberId().equals(memberId))
                    .extracting(AccountIdentityEntity::providerId)
                    .containsExactly(dexContainer.getIssuerUri());
        }

        @Test
        void setupWithoutLocalRegistration_oldSessionIdRejected() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, dexRedirectUri()));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);
            var householdId = UUID.randomUUID();
            createdHouseholdIds.add(householdId);
            var callback = loginViaDex(storeSocialLoginContext("{\"context\":\"SETUP\"}"), dexUser);
            var setupBody = """
                    {"household": {"id": "%s", "name": "Social Household"},
                     "member": {"id": "%s", "name": "Requested Name", "email": "ignored@example.com"}}
                    """.formatted(householdId, UUID.randomUUID());
            var setup = post(callback.cookies(), BASE_PATH + "/household/setup", setupBody);
            var oldSessionCookies = new LinkedHashMap<>(setup.cookies());
            oldSessionCookies.put(SESSION_COOKIE_NAME, callback.cookies().get(SESSION_COOKIE_NAME));

            // when
            var meResponse = get(oldSessionCookies, URI.create(BASE_PATH + "/me")).response();

            // then
            assertThat(meResponse.getStatus().value()).isEqualTo(401);
        }
    }

    @Nested
    class inviteJoin {

        @Test
        void joinWithoutLocalRegistration_signsInNewMemberWithDexEmail() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, dexRedirectUri()));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);
            var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
            createdHouseholdIds.add(household.id());
            var inviteToken = UUID.randomUUID();
            inviteRepository.save(new InviteEntity(null, household.id(), inviteToken, LocalDate.now().plusDays(7)));
            var callback = loginViaDex(storeSocialLoginContext(
                    "{\"context\":\"INVITE\",\"inviteToken\":\"%s\"}".formatted(inviteToken)), dexUser);
            var joinBody = """
                    {"id": "%s", "name": "Requested Name", "email": "ignored@example.com"}
                    """.formatted(UUID.randomUUID());
            var expected = new SignedInMember(dexUser.email(), "Requested Name", household.id().toString(), false);

            // when
            var join = post(callback.cookies(), BASE_PATH + "/invite/" + inviteToken + "/join", joinBody);
            var meResponse = get(join.cookies(), URI.create(BASE_PATH + "/me")).response();

            // then
            assertThat(toSignedInMember(meResponse)).isEqualTo(expected);
        }

        @Test
        void joinWithoutLocalRegistration_meReturns200() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, dexRedirectUri()));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);
            var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
            createdHouseholdIds.add(household.id());
            var inviteToken = UUID.randomUUID();
            inviteRepository.save(new InviteEntity(null, household.id(), inviteToken, LocalDate.now().plusDays(7)));
            var callback = loginViaDex(storeSocialLoginContext(
                    "{\"context\":\"INVITE\",\"inviteToken\":\"%s\"}".formatted(inviteToken)), dexUser);
            var joinBody = """
                    {"id": "%s", "name": "Requested Name", "email": "ignored@example.com"}
                    """.formatted(UUID.randomUUID());
            var join = post(callback.cookies(), BASE_PATH + "/invite/" + inviteToken + "/join", joinBody);

            // when
            var meResponse = get(join.cookies(), URI.create(BASE_PATH + "/me")).response();

            // then
            assertThat(meResponse.getStatus().value()).isEqualTo(200);
        }

        @Test
        void joinWithoutLocalRegistration_oldSessionIdRejected() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, dexRedirectUri()));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);
            var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
            createdHouseholdIds.add(household.id());
            var inviteToken = UUID.randomUUID();
            inviteRepository.save(new InviteEntity(null, household.id(), inviteToken, LocalDate.now().plusDays(7)));
            var callback = loginViaDex(storeSocialLoginContext(
                    "{\"context\":\"INVITE\",\"inviteToken\":\"%s\"}".formatted(inviteToken)), dexUser);
            var joinBody = """
                    {"id": "%s", "name": "Requested Name", "email": "ignored@example.com"}
                    """.formatted(UUID.randomUUID());
            var join = post(callback.cookies(), BASE_PATH + "/invite/" + inviteToken + "/join", joinBody);
            var oldSessionCookies = new LinkedHashMap<>(join.cookies());
            oldSessionCookies.put(SESSION_COOKIE_NAME, callback.cookies().get(SESSION_COOKIE_NAME));

            // when
            var meResponse = get(oldSessionCookies, URI.create(BASE_PATH + "/me")).response();

            // then
            assertThat(meResponse.getStatus().value()).isEqualTo(401);
        }

        @Test
        void dexLoginWithInviteContext_redirectsBackToInvitePage() throws Exception {
            // given
            dexContainer.withClient(new DexContainer.Client(DexTestcontainersConfiguration.CLIENT_ID,
                    DexTestcontainersConfiguration.CLIENT_SECRET, dexRedirectUri()));
            var dexUser = new DexContainer.User("dex-" + UUID.randomUUID(), "dex-" + UUID.randomUUID() + "@example.com", DEX_PASSWORD);
            dexContainer.withUser(dexUser);
            var inviteToken = UUID.randomUUID();
            var cookiesWithContext = storeSocialLoginContext(
                    "{\"context\":\"INVITE\",\"inviteToken\":\"%s\"}".formatted(inviteToken));

            // when
            var callback = loginViaDex(cookiesWithContext, dexUser);

            // then
            assertThat(callback.response().getResponseHeaders().getLocation())
                    .hasPath("/invite/" + inviteToken).hasQuery("social");
        }
    }

    private SignedInMember toSignedInMember(EntityExchangeResult<String> meResponse) {
        var currentUser = objectMapper.readTree(meResponse.getResponseBody());
        var member = currentUser.get("member");
        return new SignedInMember(member.get("email").asString(), member.get("name").asString(),
                currentUser.get("household").get("id").asString(), member.get("isAdmin").asBoolean());
    }

    private Map<String, String> storeSocialLoginContext(String context) {
        var csrfPreflight = get(NO_COOKIES, URI.create(BASE_PATH + "/members/availability?email=social%40example.com"));
        return post(csrfPreflight.cookies(), BASE_PATH + "/auth/social-context", context).cookies();
    }

    /**
     * Starts the social login at the backend, logs in at Dex and returns the backend's response to
     * Dex's redirect back to the callback, whose cookies carry the resulting session.
     */
    private HttpStep loginViaDex(Map<String, String> cookies, DexContainer.User dexUser) throws IOException, InterruptedException {
        var authorizationRequest = get(cookies, URI.create("/oauth2/authorization/" + REGISTRATION_ID));
        var callbackUri = loginAtDex(authorizationRequest.response().getResponseHeaders().getLocation(), dexUser);
        return get(authorizationRequest.cookies(), URI.create(callbackUri.getRawPath() + "?" + callbackUri.getRawQuery()));
    }

    /**
     * Follows Dex's redirects to its password login form, submits the user's credentials and
     * follows the redirects until Dex sends the browser back to {@link #dexRedirectUri()}.
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
            while (!location.toString().startsWith(dexRedirectUri())) {
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

    // Boot's default redirect URI {baseUrl}/login/oauth2/code/{registrationId}, as seen from this server
    private String dexRedirectUri() {
        return "http://localhost:" + port + CALLBACK_PATH;
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

    private record SignedInMember(String email, String name, String householdId, boolean admin) {
    }
}
