package eu.wiegandt.librehousehold.auth;

import com.nimbusds.jwt.SignedJWT;
import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.auth.model.AccountEntity;
import eu.wiegandt.librehousehold.auth.repository.AccountRepository;
import eu.wiegandt.librehousehold.auth.repository.FederatedIdentityRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import wf.garnier.testcontainers.dexidp.DexContainer;

import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.security.oauth2.client.registration.google.client-id=social-it-client",
                "spring.security.oauth2.client.registration.google.client-secret=social-it-secret",
                "spring.security.oauth2.client.registration.google.scope=openid,email,profile"
        }
)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
@Testcontainers
class SocialLoginIT {

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
    AccountRepository accountRepository;

    @Autowired
    FederatedIdentityRepository federatedIdentityRepository;

    private SocialLoginFlow socialLogin() {
        return new SocialLoginFlow(restTestClient, port);
    }

    @Nested
    class socialLogin {

        @Test
        void firstLogin_createsAccountAndFederatedIdentity() {
            // given
            var email = "first-" + UUID.randomUUID() + "@test.com";
            dex.withUser(new DexContainer.User("testuser", email, "test-password"));
            dex.withClient(new DexContainer.Client("social-it-client", "social-it-secret",
                    "http://localhost:" + port + "/login/oauth2/code/google"));

            // when
            socialLogin().performSocialLogin(email, "test-password");

            // then
            var account = accountRepository.findByEmail(email).orElseThrow();
            var googleIdentities = StreamSupport.stream(federatedIdentityRepository.findAll().spliterator(), false)
                    .filter(fi -> fi.accountId().equals(account.id()) && "google".equals(fi.provider()))
                    .toList();
            assertThat(googleIdentities).hasSize(1);
        }

        @Test
        void secondLogin_doesNotCreateDuplicateAccount() {
            // given
            var email = "returning-" + UUID.randomUUID() + "@test.com";
            dex.withUser(new DexContainer.User("returninguser", email, "test-password"));
            dex.withClient(new DexContainer.Client("social-it-client", "social-it-secret",
                    "http://localhost:" + port + "/login/oauth2/code/google"));

            // when
            socialLogin().performSocialLogin(email, "test-password");
            socialLogin().performSocialLogin(email, "test-password");

            // then
            var account = accountRepository.findByEmail(email).orElseThrow();
            var googleIdentities = StreamSupport.stream(federatedIdentityRepository.findAll().spliterator(), false)
                    .filter(fi -> fi.accountId().equals(account.id()) && "google".equals(fi.provider()))
                    .toList();
            assertThat(googleIdentities).hasSize(1);
        }

        @Test
        void existingLocalAccount_emailCollision_redirectsWithError() {
            // given
            var email = "collision-" + UUID.randomUUID() + "@test.com";
            accountRepository.save(new AccountEntity(UUID.randomUUID(), email, "hash"));
            dex.withUser(new DexContainer.User("collisionuser", email, "test-password"));
            dex.withClient(new DexContainer.Client("social-it-client", "social-it-secret",
                    "http://localhost:" + port + "/login/oauth2/code/google"));

            // when
            var errorLocation = socialLogin().attemptSocialLoginAndGetError(email, "test-password");

            // then
            assertThat(errorLocation).contains("error=email_already_registered");
        }

        @Test
        void accessToken_containsSocialLoginClaims() throws Exception {
            // given
            var email = "claims-" + UUID.randomUUID() + "@test.com";
            dex.withUser(new DexContainer.User("claimsuser", email, "test-password"));
            dex.withClient(new DexContainer.Client("social-it-client", "social-it-secret",
                    "http://localhost:" + port + "/login/oauth2/code/google"));
            var flow = socialLogin();
            var authResult = flow.performSocialLogin(email, "test-password");

            // when
            var tokenResponse = flow.exchangeCodeForToken(authResult.code(), authResult.codeVerifier());
            var claims = SignedJWT.parse((String) tokenResponse.get("access_token")).getJWTClaimsSet();

            // then
            assertThat(claims.getStringClaim("provider")).isEqualTo("google");
            assertThat(claims.getStringClaim("role")).isEqualTo("member");
            assertThat(claims.getClaim("household_id")).isNull();
        }
    }
}