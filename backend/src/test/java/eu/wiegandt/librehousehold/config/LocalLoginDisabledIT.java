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
 * server-side, not only in the client: without form login, {@code POST /v1/login} reaches the
 * controller, which rejects it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "librehousehold.security.local-login.enabled=false"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureRestTestClient
@ExtendWith(InstancioExtension.class)
class LocalLoginDisabledIT {

    private static final String XSRF_TOKEN_COOKIE_NAME = "XSRF-TOKEN";
    private static final String XSRF_TOKEN_HEADER_NAME = "X-XSRF-TOKEN";
    private static final String RAW_PASSWORD = "correct horse battery staple";
    private static final Problem LOCAL_LOGIN_DISABLED_PROBLEM = new Problem(403, "/problems/local-login-disabled",
            "Local login disabled", "This instance only allows logging in and registering via a social login provider.");

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
        void validCredentials_rejectedWithLocalLoginDisabledProblem() {
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
            credentials.add("email", email);
            credentials.add("password", RAW_PASSWORD);

            // when
            var response = restTestClient.post().uri("/v1/login")
                    .cookie(XSRF_TOKEN_COOKIE_NAME, xsrfToken)
                    .header(XSRF_TOKEN_HEADER_NAME, xsrfToken)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(credentials)
                    .exchange()
                    .returnResult(String.class);

            // then
            assertThat(toProblem(response)).isEqualTo(LOCAL_LOGIN_DISABLED_PROBLEM);
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
