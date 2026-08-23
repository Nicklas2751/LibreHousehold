package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import eu.wiegandt.librehousehold.household.service.AccountService;
import eu.wiegandt.librehousehold.tasks.model.TaskEntity;
import eu.wiegandt.librehousehold.tasks.repository.TaskRepository;
import eu.wiegandt.librehousehold.model.Category;
import eu.wiegandt.librehousehold.model.CategoryUpdate;
import eu.wiegandt.librehousehold.model.Expense;
import eu.wiegandt.librehousehold.model.ExpenseUpdate;
import eu.wiegandt.librehousehold.model.HouseholdUpdate;
import eu.wiegandt.librehousehold.model.MemberUpdate;
import eu.wiegandt.librehousehold.model.ReimbursementCreate;
import eu.wiegandt.librehousehold.model.ReimbursementUpdate;
import eu.wiegandt.librehousehold.model.Task;
import eu.wiegandt.librehousehold.model.TaskEdit;
import eu.wiegandt.librehousehold.model.TaskUpdate;
import eu.wiegandt.librehousehold.model.TransferOwnershipRequest;
import eu.wiegandt.librehousehold.model.UserPreferences;
import org.instancio.Instancio;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;

import java.net.URI;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;

/**
 * P1.6.3: end-to-end verification that {@code @PreAuthorize} + {@link
 * eu.wiegandt.librehousehold.household.HouseholdAccessGuard} actually reject cross-household and
 * insufficient-role access on every household-scoped endpoint (Arc42 Chapter 8, Control AC1). One
 * parametrized test per failure category, covering every endpoint from the P1.6 role table, instead
 * of one test per endpoint (project rule: ">2 similar tests -> parameterized").
 *
 * <p>Drives three real logins through the full authorization-code flow (same technique as {@link
 * AuthorizationServerConfigurationIT}) exactly once in {@link #logInAllActors()}, reused read-only
 * across every parameterized case: each login is a real multi-request PKCE round trip against
 * Testcontainers Postgres, and repeating it per test case (as {@code @BeforeEach} inline setup
 * normally would) would multiply the ~30 endpoint cases by three logins each for no additional
 * coverage — the three sessions themselves are never mutated by any test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=54330",
        "librehousehold.security.oauth2-authorization-server.issuer=http://localhost:54330",
        "librehousehold.security.oauth2-client.redirect-uri=http://localhost:54330/login/oauth2/code/spa-backend-client",
        "librehousehold.security.oauth2-client.client-secret=test-client-secret"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureRestTestClient
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HouseholdAccessControlIT {

    private static final String BASE_PATH = "/v1";
    private static final String CLIENT_ID = "spa-backend-client";
    private static final String RAW_PASSWORD = "correct horse battery staple";
    private static final String XSRF_TOKEN_COOKIE = "XSRF-TOKEN";
    private static final String XSRF_TOKEN_HEADER = "X-XSRF-TOKEN";
    private static final Pattern REMAINING_PATH_PLACEHOLDER = Pattern.compile("\\{[a-zA-Z]+}");

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TaskRepository taskRepository;

    private UUID householdAId;
    private UUID householdBId;
    private UUID adminOfHouseholdAId;
    private UUID memberOfHouseholdAId;
    private UUID memberOfHouseholdBId;
    private UUID taskOfHouseholdBId;
    private Map<String, String> adminOfHouseholdACookies;
    private Map<String, String> memberOfHouseholdACookies;
    private Map<String, String> memberOfHouseholdBCookies;
    private String xsrfToken;

    @BeforeAll
    void logInAllActors() {
        var householdA = householdRepository.save(Instancio.create(HouseholdEntity.class));
        householdAId = householdA.id();
        var householdB = householdRepository.save(Instancio.create(HouseholdEntity.class));
        householdBId = householdB.id();

        var adminAEmail = "admin-a-" + UUID.randomUUID() + "@example.com";
        var memberAEmail = "member-a-" + UUID.randomUUID() + "@example.com";
        var memberBEmail = "member-b-" + UUID.randomUUID() + "@example.com";
        adminOfHouseholdAId = createMemberWithAccount(householdAId, adminAEmail, true).getId();
        memberOfHouseholdAId = createMemberWithAccount(householdAId, memberAEmail, false).getId();
        memberOfHouseholdBId = createMemberWithAccount(householdBId, memberBEmail, false).getId();
        taskOfHouseholdBId = taskRepository.save(new TaskEntity(
                UUID.randomUUID(), householdBId, null, "Task of household B", null, LocalDate.now(), false, null, null)).getId();

        adminOfHouseholdACookies = login(adminAEmail);
        memberOfHouseholdACookies = login(memberAEmail);
        memberOfHouseholdBCookies = login(memberBEmail);
        xsrfToken = fetchXsrfToken();
    }

    @AfterAll
    void tearDown() {
        taskRepository.deleteByHouseholdId(householdBId);
        memberRepository.deleteByHouseholdId(householdAId);
        memberRepository.deleteByHouseholdId(householdBId);
        householdRepository.deleteById(householdAId);
        householdRepository.deleteById(householdBId);
    }

    @ParameterizedTest
    @MethodSource("allProtectedEndpoints")
    void endpoint_memberOfDifferentHousehold_returns403(Endpoint endpoint) {
        // when
        var response = exchange(endpoint, householdAId, UUID.randomUUID(), memberOfHouseholdBCookies);

        // then
        assertThat(response.getStatus().value()).isEqualTo(403);
    }

    @ParameterizedTest
    @MethodSource("adminOnlyEndpoints")
    void adminOnlyEndpoint_memberWithoutAdminRights_returns403(Endpoint endpoint) {
        // when
        var response = exchange(endpoint, householdAId, UUID.randomUUID(), memberOfHouseholdACookies);

        // then
        assertThat(response.getStatus().value()).isEqualTo(403);
    }

    @ParameterizedTest
    @MethodSource("selfOnlyEndpoints")
    void selfOnlyEndpoint_adminOfSameHouseholdOnDifferentMember_returns403(Endpoint endpoint) {
        // when
        var response = exchange(endpoint, householdAId, memberOfHouseholdAId, adminOfHouseholdACookies);

        // then
        assertThat(response.getStatus().value()).isEqualTo(403);
    }

    @ParameterizedTest
    @MethodSource("selfRestrictedEndpoints")
    void selfRestrictedEndpoint_differentNonAdminMemberOfSameHousehold_returns403(Endpoint endpoint) {
        // when
        var response = exchange(endpoint, householdAId, adminOfHouseholdAId, memberOfHouseholdACookies);

        // then
        assertThat(response.getStatus().value()).isEqualTo(403);
    }

    @ParameterizedTest
    @MethodSource("crossHouseholdSubResourceEndpoints")
    void endpoint_ownCorrectHouseholdIdButSubResourceIdFromDifferentHousehold_returns404(Endpoint endpoint) {
        // given — the actor's own householdId is correct, but the taskId/memberId in the path
        // belongs to a completely different household
        var foreignSubResourceId = endpoint.pathTemplate().contains("{taskId}") ? taskOfHouseholdBId : memberOfHouseholdBId;

        // when
        var response = exchangeWithSubResourceId(endpoint, householdAId, foreignSubResourceId, adminOfHouseholdACookies);

        // then
        assertThat(response.getStatus().value()).isEqualTo(404);
    }

    @Test
    void transferOwnership_memberIdFromDifferentHousehold_returns404() {
        // given — adminOfHouseholdA's own householdId is correct, but the memberId in the body
        // belongs to a completely different household
        var endpoint = new Endpoint(HttpMethod.PUT, BASE_PATH + "/household/{householdId}/admin",
                new TransferOwnershipRequest(memberOfHouseholdBId));

        // when
        var response = exchangeWithSubResourceId(endpoint, householdAId, null, adminOfHouseholdACookies);

        // then
        assertThat(response.getStatus().value()).isEqualTo(404);
    }

    @Test
    void adminOnlyEndpoint_afterAdminRevokedInSameSession_returns403WithoutReLogin() {
        // given — a dedicated household/admin pair so revoking admin rights here does not affect
        // any other test's shared session
        var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
        var adminEmail = "revoked-admin-" + UUID.randomUUID() + "@example.com";
        var newAdminId = createMemberWithAccount(household.id(), "new-admin-" + UUID.randomUUID() + "@example.com", false).getId();
        createMemberWithAccount(household.id(), adminEmail, true);
        var adminCookies = login(adminEmail);
        var renameHousehold = new Endpoint(HttpMethod.PATCH, BASE_PATH + "/household/{householdId}", new HouseholdUpdate("Renamed once"));
        var beforeRevocation = exchange(renameHousehold, household.id(), null, adminCookies);
        assertThat(beforeRevocation.getStatus().value()).isEqualTo(204);
        var transferOwnership = new Endpoint(HttpMethod.PUT, BASE_PATH + "/household/{householdId}/admin",
                new TransferOwnershipRequest(newAdminId));
        var transferResponse = exchange(transferOwnership, household.id(), null, adminCookies);
        assertThat(transferResponse.getStatus().value()).isEqualTo(204);

        // when — the same, still-open session tries another admin-only action
        var response = exchange(renameHousehold, household.id(), null, adminCookies);

        // then
        assertThat(response.getStatus().value()).isEqualTo(403);

        memberRepository.deleteByHouseholdId(household.id());
        householdRepository.deleteById(household.id());
    }

    private static Stream<Endpoint> memberEndpoints() {
        return Stream.of(
                new Endpoint(HttpMethod.GET, BASE_PATH + "/household/{householdId}/tasks", null),
                new Endpoint(HttpMethod.POST, BASE_PATH + "/household/{householdId}/tasks",
                        new Task(UUID.randomUUID(), "Clean kitchen", LocalDate.now())),
                new Endpoint(HttpMethod.PUT, BASE_PATH + "/household/{householdId}/tasks/{taskId}",
                        new TaskEdit("Clean kitchen", LocalDate.now())),
                new Endpoint(HttpMethod.PATCH, BASE_PATH + "/household/{householdId}/tasks/{taskId}", new TaskUpdate()),
                new Endpoint(HttpMethod.DELETE, BASE_PATH + "/household/{householdId}/tasks/{taskId}", null),
                new Endpoint(HttpMethod.GET, BASE_PATH + "/household/{householdId}/expenses", null),
                new Endpoint(HttpMethod.GET, BASE_PATH + "/household/{householdId}/expenses/{expenseId}", null),
                new Endpoint(HttpMethod.POST, BASE_PATH + "/household/{householdId}/expenses",
                        new Expense(UUID.randomUUID(), "Groceries run", 12.5, UUID.randomUUID(), LocalDate.now(), UUID.randomUUID())),
                new Endpoint(HttpMethod.PATCH, BASE_PATH + "/household/{householdId}/expenses/{expenseId}", new ExpenseUpdate()),
                new Endpoint(HttpMethod.DELETE, BASE_PATH + "/household/{householdId}/expenses/{expenseId}", null),
                new Endpoint(HttpMethod.GET, BASE_PATH + "/household/{householdId}/expenses/debts/{payerId}/{debtorId}", null),
                new Endpoint(HttpMethod.GET, BASE_PATH + "/household/{householdId}/categories", null),
                new Endpoint(HttpMethod.POST, BASE_PATH + "/household/{householdId}/categories",
                        new Category(UUID.randomUUID(), "Groceries")),
                new Endpoint(HttpMethod.PATCH, BASE_PATH + "/household/{householdId}/categories/{categoryId}", new CategoryUpdate()),
                new Endpoint(HttpMethod.DELETE, BASE_PATH + "/household/{householdId}/categories/{categoryId}", null),
                new Endpoint(HttpMethod.GET, BASE_PATH + "/household/{householdId}/reimbursements", null),
                new Endpoint(HttpMethod.POST, BASE_PATH + "/household/{householdId}/reimbursements",
                        new ReimbursementCreate(10.0, UUID.randomUUID(), UUID.randomUUID())),
                new Endpoint(HttpMethod.PATCH, BASE_PATH + "/household/{householdId}/reimbursements/{reimbursementId}", new ReimbursementUpdate()),
                new Endpoint(HttpMethod.GET, BASE_PATH + "/household/{householdId}/financials/{userId}/summary", null),
                new Endpoint(HttpMethod.GET, BASE_PATH + "/household/{householdId}/financials/{userId}/balances", null),
                new Endpoint(HttpMethod.GET, BASE_PATH + "/household/{householdId}/statistics?period=THIS_MONTH", null),
                new Endpoint(HttpMethod.GET, BASE_PATH + "/household/{householdId}/members", null),
                new Endpoint(HttpMethod.GET, BASE_PATH + "/household/{householdId}/members/{memberId}", null)
        );
    }

    private static Stream<Endpoint> adminOnlyEndpoints() {
        return Stream.of(
                new Endpoint(HttpMethod.PATCH, BASE_PATH + "/household/{householdId}", new HouseholdUpdate("New Household Name")),
                new Endpoint(HttpMethod.DELETE, BASE_PATH + "/household/{householdId}", null),
                new Endpoint(HttpMethod.GET, BASE_PATH + "/household/{householdId}/invite", null),
                new Endpoint(HttpMethod.POST, BASE_PATH + "/household/{householdId}/invite", null),
                new Endpoint(HttpMethod.PUT, BASE_PATH + "/household/{householdId}/admin", new TransferOwnershipRequest(UUID.randomUUID())),
                new Endpoint(HttpMethod.DELETE, BASE_PATH + "/household/{householdId}/members/{memberId}", null)
        );
    }

    private static Stream<Endpoint> selfOrAdminEndpoints() {
        return Stream.of(
                new Endpoint(HttpMethod.PATCH, BASE_PATH + "/household/{householdId}/members/{memberId}", new MemberUpdate())
        );
    }

    private static Stream<Endpoint> selfOnlyEndpoints() {
        return Stream.of(
                new Endpoint(HttpMethod.DELETE, BASE_PATH + "/household/{householdId}/members/{memberId}/account", null),
                new Endpoint(HttpMethod.PATCH, BASE_PATH + "/household/{householdId}/members/{memberId}/preferences", new UserPreferences())
        );
    }

    private static Stream<Endpoint> selfRestrictedEndpoints() {
        return Stream.concat(selfOnlyEndpoints(), selfOrAdminEndpoints());
    }

    private static Stream<Endpoint> allProtectedEndpoints() {
        return Stream.of(memberEndpoints(), adminOnlyEndpoints(), selfRestrictedEndpoints()).flatMap(stream -> stream);
    }

    private static Stream<Endpoint> crossHouseholdSubResourceEndpoints() {
        return Stream.of(
                new Endpoint(HttpMethod.PUT, BASE_PATH + "/household/{householdId}/tasks/{taskId}",
                        new TaskEdit("Clean kitchen", LocalDate.now())),
                new Endpoint(HttpMethod.PATCH, BASE_PATH + "/household/{householdId}/tasks/{taskId}", new TaskUpdate()),
                new Endpoint(HttpMethod.DELETE, BASE_PATH + "/household/{householdId}/tasks/{taskId}", null),
                new Endpoint(HttpMethod.GET, BASE_PATH + "/household/{householdId}/members/{memberId}", null),
                new Endpoint(HttpMethod.PATCH, BASE_PATH + "/household/{householdId}/members/{memberId}", new MemberUpdate()),
                new Endpoint(HttpMethod.DELETE, BASE_PATH + "/household/{householdId}/members/{memberId}", null)
        );
    }

    private MemberEntity createMemberWithAccount(UUID householdId, String email, boolean isAdmin) {
        var member = memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), householdId)
                .set(field(MemberEntity::email), email)
                .set(field(MemberEntity::isAdmin), isAdmin)
                .create());
        accountService.createAccount(member.getId(), RAW_PASSWORD);
        return member;
    }

    private EntityExchangeResult<String> exchange(Endpoint endpoint, UUID householdId, UUID memberId, Map<String, String> actorCookies) {
        var uri = URI.create(resolvePath(endpoint.pathTemplate(), householdId, memberId));
        return sendRequest(endpoint, uri, actorCookies);
    }

    /**
     * Like {@link #exchange}, but substitutes every path placeholder other than {@code {householdId}}
     * (e.g. {@code {taskId}}, {@code {memberId}}) with {@code subResourceId} — used to prove that a
     * sub-resource id from a different household is rejected even when {@code householdId} is correct.
     */
    private EntityExchangeResult<String> exchangeWithSubResourceId(Endpoint endpoint, UUID householdId, UUID subResourceId,
                                                                    Map<String, String> actorCookies) {
        var withHouseholdId = endpoint.pathTemplate().replace("{householdId}", householdId.toString());
        var resolvedPath = subResourceId == null
                ? withHouseholdId
                : REMAINING_PATH_PLACEHOLDER.matcher(withHouseholdId).replaceAll(subResourceId.toString());
        return sendRequest(endpoint, URI.create(resolvedPath), actorCookies);
    }

    private EntityExchangeResult<String> sendRequest(Endpoint endpoint, URI uri, Map<String, String> actorCookies) {
        RestTestClient.RequestBodySpec spec = restTestClient.method(endpoint.method()).uri(uri);
        spec = withCookies(spec, actorCookies);
        if (endpoint.method() != HttpMethod.GET) {
            spec = spec.cookie(XSRF_TOKEN_COOKIE, xsrfToken).header(XSRF_TOKEN_HEADER, xsrfToken);
        }
        RestTestClient.RequestHeadersSpec<?> requestReadyToSend = endpoint.body() == null
                ? spec
                : spec.contentType(MediaType.APPLICATION_JSON).body(endpoint.body());
        return requestReadyToSend.exchange().returnResult(String.class);
    }

    /**
     * Applies the actor's session cookies, excluding any XSRF-TOKEN cookie collected incidentally
     * during login: {@link #sendRequest} always sets the one shared, freshly-fetched {@link
     * #xsrfToken} explicitly for state-changing requests, and letting the actor's own (stale, from a
     * different double-submit exchange) XSRF-TOKEN cookie sneak in as a second, conflicting
     * "Cookie: XSRF-TOKEN=..." header entry made CSRF validation fail for every non-GET request.
     */
    private RestTestClient.RequestBodySpec withCookies(RestTestClient.RequestBodySpec spec, Map<String, String> cookies) {
        var result = spec;
        for (var cookie : cookies.entrySet()) {
            if (!cookie.getKey().equals(XSRF_TOKEN_COOKIE)) {
                result = result.cookie(cookie.getKey(), cookie.getValue());
            }
        }
        return result;
    }

    private String resolvePath(String template, UUID householdId, UUID memberId) {
        var resolved = template.replace("{householdId}", householdId.toString());
        if (memberId != null) {
            resolved = resolved.replace("{memberId}", memberId.toString());
        }
        return REMAINING_PATH_PLACEHOLDER.matcher(resolved).replaceAll(_ -> UUID.randomUUID().toString());
    }

    private String fetchXsrfToken() {
        var response = get(Map.of(), URI.create(BASE_PATH + "/members/availability?email=" + UUID.randomUUID() + "%40example.com"));
        return response.response().getResponseCookies().getFirst(XSRF_TOKEN_COOKIE).getValue();
    }

    /**
     * Drives a full, real login (authorization-code flow with the backend as its own OAuth2 client,
     * see DD-7) and returns the resulting authenticated session cookies. Adapted from the equivalent
     * helper in {@link AuthorizationServerConfigurationIT}.
     */
    private Map<String, String> login(String email) {
        var loginPage = requestLoginPageViaAuthorizationCodeFlow();
        var loginResult = submitLogin(loginPage.cookies(), loginPage.csrfToken(), email);
        var cookiesAfterLogin = mergeCookies(loginPage.cookies(), loginResult);
        var authorizeAgain = get(cookiesAfterLogin, loginResult.getResponseHeaders().getLocation());
        var callback = get(authorizeAgain.cookies(), authorizeAgain.response().getResponseHeaders().getLocation());
        return callback.cookies();
    }

    private LoginPage requestLoginPageViaAuthorizationCodeFlow() {
        var authorizationRequest = get(Map.of(), URI.create("/oauth2/authorization/" + CLIENT_ID));
        var authorizeRequest = get(authorizationRequest.cookies(), authorizationRequest.response().getResponseHeaders().getLocation());
        var loginPageRequest = get(authorizeRequest.cookies(), authorizeRequest.response().getResponseHeaders().getLocation());
        var csrfToken = extractCsrfToken(loginPageRequest.response().getResponseBody());
        return new LoginPage(loginPageRequest.cookies(), csrfToken);
    }

    private HttpStep get(Map<String, String> cookies, URI uri) {
        var spec = restTestClient.get().uri(uri);
        for (var cookie : cookies.entrySet()) {
            spec = spec.cookie(cookie.getKey(), cookie.getValue());
        }
        var response = spec.exchange().returnResult(String.class);
        return new HttpStep(mergeCookies(cookies, response), response);
    }

    private EntityExchangeResult<Void> submitLogin(Map<String, String> cookies, String csrfToken, String email) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("username", email);
        form.add("password", RAW_PASSWORD);
        form.add("_csrf", csrfToken);
        RestTestClient.RequestBodySpec spec = restTestClient.post().uri("/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED);
        for (var cookie : cookies.entrySet()) {
            spec = spec.cookie(cookie.getKey(), cookie.getValue());
        }
        return spec.body(form).exchange().returnResult(Void.class);
    }

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

    private record Endpoint(HttpMethod method, String pathTemplate, Object body) {
    }
}
