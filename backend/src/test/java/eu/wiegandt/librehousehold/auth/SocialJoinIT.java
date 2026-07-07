package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.household.model.InviteEntity;
import eu.wiegandt.librehousehold.household.repository.InviteRepository;
import eu.wiegandt.librehousehold.model.HouseholdSetupResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import wf.garnier.testcontainers.dexidp.DexContainer;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.security.oauth2.client.registration.google.client-id=social-join-it-client",
                "spring.security.oauth2.client.registration.google.client-secret=social-join-it-secret",
                "spring.security.oauth2.client.registration.google.scope=openid,email,profile"
        }
)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
@Testcontainers
class SocialJoinIT {

    @Container
    static DexContainer dex = new DexContainer(DexContainer.DEFAULT_IMAGE_NAME.withTag(DexContainer.DEFAULT_TAG));

    @DynamicPropertySource
    static void dexProperties(DynamicPropertyRegistry registry) {
        SocialLoginFlow.registerDexProperties(registry, dex);
    }

    @LocalServerPort
    int port;

    @Autowired
    RestTestClient restTestClient;

    @Autowired
    InviteRepository inviteRepository;

    private SocialLoginFlow socialLogin() {
        return new SocialLoginFlow(restTestClient, port);
    }

    @Nested
    class socialJoin {

        @Test
        void newSocialUser_joinsCorrectInvitedHousehold() {
            // given
            var householdA = registerHousehold("Household A");
            registerHousehold("Household B");
            var email = "join-" + UUID.randomUUID() + "@test.com";
            dex.withUser(new DexContainer.User("joinuser", email, "test-password"));
            dex.withClient(new DexContainer.Client("social-join-it-client", "social-join-it-secret",
                    "http://localhost:" + port + "/login/oauth2/code/google"));
            var accessToken = socialLogin().performSocialLoginAndGetAccessToken(email, "test-password");

            // when
            restTestClient.post()
                    .uri("/v1/household/join")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("token", householdA.getInviteToken().toString(), "memberName", "Dex User"))
                    .exchange()
                    .expectStatus().isCreated();

            // then
            assertThat(inviteRepository.findByToken(householdA.getInviteToken()))
                    .hasValueSatisfying(invite -> assertThat(invite.householdId())
                            .isEqualTo(householdA.getHousehold().getId()));
        }

        @Test
        void accountAlreadyMemberOfAHousehold_returns409() {
            // given
            var householdA = registerHousehold("Household A");
            var householdB = registerHousehold("Household B");
            var email = "already-member-" + UUID.randomUUID() + "@test.com";
            dex.withUser(new DexContainer.User("alreadymember", email, "test-password"));
            dex.withClient(new DexContainer.Client("social-join-it-client", "social-join-it-secret",
                    "http://localhost:" + port + "/login/oauth2/code/google"));
            var accessToken = socialLogin().performSocialLoginAndGetAccessToken(email, "test-password");
            restTestClient.post()
                    .uri("/v1/household/join")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("token", householdA.getInviteToken().toString(), "memberName", "Dex User"))
                    .exchange()
                    .expectStatus().isCreated();

            // when
            var response = restTestClient.post()
                    .uri("/v1/household/join")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("token", householdB.getInviteToken().toString(), "memberName", "Dex User"))
                    .exchange();

            // then
            response.expectStatus().isEqualTo(409);
        }

        @Test
        void expiredInvite_returns404() {
            // given
            var household = registerHousehold("Household A");
            var expiredInvite = inviteRepository.save(new InviteEntity(
                    null, household.getHousehold().getId(), UUID.randomUUID(), LocalDate.now().minusDays(1)));
            var email = "expired-" + UUID.randomUUID() + "@test.com";
            dex.withUser(new DexContainer.User("expireduser", email, "test-password"));
            dex.withClient(new DexContainer.Client("social-join-it-client", "social-join-it-secret",
                    "http://localhost:" + port + "/login/oauth2/code/google"));
            var accessToken = socialLogin().performSocialLoginAndGetAccessToken(email, "test-password");

            // when
            var response = restTestClient.post()
                    .uri("/v1/household/join")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("token", expiredInvite.token().toString(), "memberName", "Dex User"))
                    .exchange();

            // then
            response.expectStatus().isNotFound();
        }

        @Test
        void noAuthentication_returns401() {
            // given
            var household = registerHousehold("Household A");
            var requestBody = Map.of("token", household.getInviteToken().toString(), "memberName", "Dex User");

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

    private HouseholdSetupResponse registerHousehold(String householdName) {
        var requestBody = Map.of(
                "householdName", householdName,
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
}