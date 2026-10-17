package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.InviteEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.InviteRepository;
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
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;

/**
 * Verifies that turning local login off also locks the form login and registering with a password
 * server-side, not only in the client. Uses a port picked before startup for the reason given in {@link CsrfProtectionIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "librehousehold.security.oauth2-client.client-secret=test-client-secret",
        "librehousehold.security.local-login.enabled=false"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureRestTestClient
@ExtendWith(InstancioExtension.class)
class LocalLoginDisabledIT {

    private static final int SERVER_PORT = TestSocketUtils.findAvailableTcpPort();
    private static final String SERVER_BASE_URL = "http://localhost:" + SERVER_PORT;

    private static final String XSRF_TOKEN_COOKIE_NAME = "XSRF-TOKEN";
    private static final String XSRF_TOKEN_HEADER_NAME = "X-XSRF-TOKEN";
    private static final String RAW_PASSWORD = "correct horse battery staple";
    private static final Problem LOCAL_LOGIN_DISABLED_PROBLEM = new Problem(403, "/problems/local-login-disabled",
            "Local login disabled", "This instance only allows registering via a social login provider.");

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
    private HouseholdRepository householdRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private InviteRepository inviteRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID createdHouseholdId;

    @AfterEach
    void tearDown() {
        if (createdHouseholdId != null) {
            inviteRepository.deleteByHouseholdId(createdHouseholdId);
            memberRepository.deleteByHouseholdId(createdHouseholdId);
            householdRepository.deleteById(createdHouseholdId);
        }
    }

    @Nested
    class formLogin {

        @Test
        void validCredentials_redirectsToLoginWithLocalLoginDisabledReason() {
            // given
            var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
            createdHouseholdId = household.id();
            var email = "local-" + UUID.randomUUID() + "@example.com";
            var member = memberRepository.save(Instancio.of(MemberEntity.class)
                    .set(field(MemberEntity::householdId), household.id())
                    .set(field(MemberEntity::email), email)
                    .create());
            accountService.createAccount(member.getId(), RAW_PASSWORD);
            accountService.markEmailVerified(member.getId());
            var xsrfToken = restTestClient.get().uri("/v1/members/availability?email=local%40example.com")
                    .exchange().returnResult(String.class)
                    .getResponseCookies().getFirst(XSRF_TOKEN_COOKIE_NAME).getValue();
            var credentials = new LinkedMultiValueMap<String, String>();
            credentials.add("username", email);
            credentials.add("password", RAW_PASSWORD);

            // when
            var response = restTestClient.post().uri("/login")
                    .cookie(XSRF_TOKEN_COOKIE_NAME, xsrfToken)
                    .header(XSRF_TOKEN_HEADER_NAME, xsrfToken)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(credentials)
                    .exchange()
                    .returnResult(String.class);

            // then
            assertThat(response.getResponseHeaders().getLocation())
                    .hasPath("/login").hasQuery("error&reason=local-login-disabled");
        }
    }

    @Nested
    class registrationWithPassword {

        @Test
        void householdSetup_rejectedWithLocalLoginDisabledProblem() {
            // given
            var xsrfToken = restTestClient.get().uri("/v1/members/availability?email=local%40example.com")
                    .exchange().returnResult(String.class)
                    .getResponseCookies().getFirst(XSRF_TOKEN_COOKIE_NAME).getValue();
            var setupBody = """
                    {"household": {"id": "%s", "name": "Local Household"},
                     "member": {"id": "%s", "name": "Max Mustermann", "email": "local-%s@example.com"},
                     "localRegistration": {"password": "correct horse battery staple"}}
                    """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

            // when
            var response = restTestClient.post().uri("/v1/household/setup")
                    .cookie(XSRF_TOKEN_COOKIE_NAME, xsrfToken)
                    .header(XSRF_TOKEN_HEADER_NAME, xsrfToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(setupBody)
                    .exchange()
                    .returnResult(String.class);

            // then
            assertThat(toProblem(response)).isEqualTo(LOCAL_LOGIN_DISABLED_PROBLEM);
        }

        @Test
        void inviteJoin_rejectedWithLocalLoginDisabledProblem() {
            // given
            var household = householdRepository.save(Instancio.create(HouseholdEntity.class));
            createdHouseholdId = household.id();
            var inviteToken = UUID.randomUUID();
            inviteRepository.save(new InviteEntity(null, household.id(), inviteToken, LocalDate.now().plusDays(7)));
            var xsrfToken = restTestClient.get().uri("/v1/members/availability?email=local%40example.com")
                    .exchange().returnResult(String.class)
                    .getResponseCookies().getFirst(XSRF_TOKEN_COOKIE_NAME).getValue();
            var joinBody = """
                    {"id": "%s", "name": "Max Mustermann", "email": "local-%s@example.com",
                     "localRegistration": {"password": "correct horse battery staple"}}
                    """.formatted(UUID.randomUUID(), UUID.randomUUID());

            // when
            var response = restTestClient.post().uri("/v1/invite/" + inviteToken + "/join")
                    .cookie(XSRF_TOKEN_COOKIE_NAME, xsrfToken)
                    .header(XSRF_TOKEN_HEADER_NAME, xsrfToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(joinBody)
                    .exchange()
                    .returnResult(String.class);

            // then
            assertThat(toProblem(response)).isEqualTo(LOCAL_LOGIN_DISABLED_PROBLEM);
        }
    }

    private Problem toProblem(EntityExchangeResult<String> response) {
        var body = objectMapper.readTree(response.getResponseBody());
        return new Problem(response.getStatus().value(), body.path("type").asString(), body.path("title").asString(),
                body.path("detail").asString());
    }

    private record Problem(int status, String type, String title, String detail) {
    }
}
