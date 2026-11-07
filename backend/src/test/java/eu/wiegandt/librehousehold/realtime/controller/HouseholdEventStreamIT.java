package eu.wiegandt.librehousehold.realtime.controller;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.model.HouseholdEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import eu.wiegandt.librehousehold.household.service.AccountService;
import eu.wiegandt.librehousehold.realtime.SseTestClient;
import eu.wiegandt.librehousehold.realtime.SseTestClient.SseEvent;
import org.instancio.Instancio;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;

/**
 * The household event stream over the real server (ADR-019, {@code api/asyncapi.yml}). The stream
 * is read with {@link SseTestClient}; login and cookies work like in
 * {@code MemberSessionInvalidationIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // Same properties as FormLoginIT and MemberSessionInvalidationIT, so all share one cached
        // context; the login limits themselves are covered by RateLimitingIT
        "librehousehold.security.rate-limit.login-per-ip.max-attempts=10000",
        "librehousehold.security.rate-limit.login.max-attempts=10000"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureRestTestClient
class HouseholdEventStreamIT {

    private static final String BASE_PATH = "/v1";
    private static final String RAW_PASSWORD = "correct horse battery staple";
    private static final String XSRF_COOKIE = "XSRF-TOKEN";
    private static final String XSRF_HEADER = "X-XSRF-TOKEN";
    private static final Map<String, String> NO_COOKIES = Map.of();
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(5);

    @LocalServerPort
    private int port;

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void openStream_member_streamingResponseHead() throws Exception {
        // given
        var member = createVerifiedMember(createHousehold());
        var expected = new ResponseHead(200, MediaType.TEXT_EVENT_STREAM_VALUE, "no-store", "no");

        // when
        try (var stream = SseTestClient.open(streamUri(member.householdId()), loggedInCookies(member))) {

            // then
            assertThat(ResponseHead.of(stream)).isEqualTo(expected);
        }
    }

    @Test
    void openStream_member_firstBlockSetsReconnectDelay() throws Exception {
        // given
        var member = createVerifiedMember(createHousehold());
        var expected = new SseEvent(null, null, 5000L, List.of());

        // when
        try (var stream = SseTestClient.open(streamUri(member.householdId()), loggedInCookies(member))) {

            // then
            assertThat(stream.awaitEvent(EVENT_TIMEOUT)).isEqualTo(expected);
        }
    }

    private record ResponseHead(int status, @Nullable String contentType, @Nullable String cacheControl,
                                @Nullable String accelBuffering) {

        static ResponseHead of(SseTestClient stream) {
            var headers = stream.headers();
            return new ResponseHead(stream.status(),
                    headers.firstValue("Content-Type").orElse(null),
                    headers.firstValue("Cache-Control").orElse(null),
                    headers.firstValue("X-Accel-Buffering").orElse(null));
        }
    }

    private URI streamUri(UUID householdId) {
        return URI.create("http://localhost:" + port + BASE_PATH + "/household/" + householdId + "/events");
    }

    private UUID createHousehold() {
        return householdRepository.save(Instancio.create(HouseholdEntity.class)).id();
    }

    private MemberEntity createVerifiedMember(UUID householdId) {
        var member = memberRepository.save(Instancio.of(MemberEntity.class)
                .set(field(MemberEntity::householdId), householdId)
                .set(field(MemberEntity::email), "event-stream-" + UUID.randomUUID() + "@example.com")
                .set(field(MemberEntity::isAdmin), false)
                .create());
        accountService.createAccount(member.getId(), RAW_PASSWORD);
        accountRepository.markEmailVerified(member.getId());
        return member;
    }

    // Every login issues a new XSRF-TOKEN, which the merge keeps for follow-up requests
    private Map<String, String> loggedInCookies(MemberEntity member) {
        var cookies = merge(NO_COOKIES, withCookies(restTestClient.get().uri(BASE_PATH + "/auth/discover"), NO_COOKIES)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .returnResult(String.class));
        var form = new LinkedMultiValueMap<String, String>();
        form.add("email", member.email());
        form.add("password", RAW_PASSWORD);
        var loginResponse = withCookies(restTestClient.post().uri(BASE_PATH + "/login"), cookies)
                .header(XSRF_HEADER, cookies.get(XSRF_COOKIE))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .exchange()
                .returnResult(String.class);
        return merge(cookies, loginResponse);
    }

    private <S extends RestTestClient.RequestHeadersSpec<?>> S withCookies(S spec, Map<String, String> cookies) {
        cookies.forEach(spec::cookie);
        return spec;
    }

    private Map<String, String> merge(Map<String, String> cookies, ExchangeResult response) {
        var merged = new LinkedHashMap<>(cookies);
        response.getResponseCookies().values().stream()
                .flatMap(List::stream)
                .forEach(cookie -> merged.put(cookie.getName(), cookie.getValue()));
        return merged;
    }
}
