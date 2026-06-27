package eu.wiegandt.librehousehold.auth;

import com.nimbusds.jwt.SignedJWT;
import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.auth.model.AccountEntity;
import eu.wiegandt.librehousehold.auth.repository.AccountRepository;
import eu.wiegandt.librehousehold.auth.repository.FederatedIdentityRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import wf.garnier.testcontainers.dexidp.DexContainer;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.security.oauth2.client.registration.google.client-id=social-it-client",
                "spring.security.oauth2.client.registration.google.client-secret=social-it-secret",
                "spring.security.oauth2.client.registration.google.scope=openid,email,profile"
        }
)
@Import(TestcontainersConfiguration.class)
@Testcontainers
class SocialLoginIT {

    @Container
    static DexContainer dex = new DexContainer(DexContainer.DEFAULT_IMAGE_NAME.withTag(DexContainer.DEFAULT_TAG));

    @DynamicPropertySource
    static void dexProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.client.provider.google.issuer-uri",
                () -> dex.getIssuerUri());
        registry.add("spring.security.oauth2.client.provider.google.authorization-uri",
                () -> dex.getIssuerUri() + "/auth");
        registry.add("spring.security.oauth2.client.provider.google.token-uri",
                () -> dex.getIssuerUri() + "/token");
        registry.add("spring.security.oauth2.client.provider.google.jwk-set-uri",
                () -> dex.getIssuerUri() + "/keys");
        registry.add("spring.security.oauth2.client.provider.google.user-info-uri",
                () -> dex.getIssuerUri() + "/userinfo");
    }

    @LocalServerPort
    int port;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    FederatedIdentityRepository federatedIdentityRepository;

    @Nested
    class socialLogin {

        @Test
        void firstLogin_createsAccountAndFederatedIdentity() throws Exception {
            // given
            var email = "first-" + UUID.randomUUID() + "@test.com";
            dex.withUser(new DexContainer.User("testuser", email, "test-password"));
            dex.withClient(new DexContainer.Client("social-it-client", "social-it-secret",
                    "http://localhost:" + port + "/login/oauth2/code/google"));

            // when
            performSocialLogin(email, "test-password");

            // then
            var account = accountRepository.findByEmail(email).orElseThrow();
            var googleIdentities = StreamSupport.stream(federatedIdentityRepository.findAll().spliterator(), false)
                    .filter(fi -> fi.accountId().equals(account.id()) && "google".equals(fi.provider()))
                    .toList();
            assertThat(googleIdentities).hasSize(1);
        }

        @Test
        void secondLogin_doesNotCreateDuplicateAccount() throws Exception {
            // given
            var email = "returning-" + UUID.randomUUID() + "@test.com";
            dex.withUser(new DexContainer.User("returninguser", email, "test-password"));
            dex.withClient(new DexContainer.Client("social-it-client", "social-it-secret",
                    "http://localhost:" + port + "/login/oauth2/code/google"));

            // when
            performSocialLogin(email, "test-password");
            performSocialLogin(email, "test-password");

            // then
            var account = accountRepository.findByEmail(email).orElseThrow();
            var googleIdentities = StreamSupport.stream(federatedIdentityRepository.findAll().spliterator(), false)
                    .filter(fi -> fi.accountId().equals(account.id()) && "google".equals(fi.provider()))
                    .toList();
            assertThat(googleIdentities).hasSize(1);
        }

        @Test
        void existingLocalAccount_emailCollision_redirectsWithError() throws Exception {
            // given
            var email = "collision-" + UUID.randomUUID() + "@test.com";
            accountRepository.save(new AccountEntity(UUID.randomUUID(), email, "hash"));
            dex.withUser(new DexContainer.User("collisionuser", email, "test-password"));
            dex.withClient(new DexContainer.Client("social-it-client", "social-it-secret",
                    "http://localhost:" + port + "/login/oauth2/code/google"));

            // when
            var errorLocation = attemptSocialLoginAndGetError(email, "test-password");

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
            var authResult = performSocialLogin(email, "test-password");

            // when
            var tokenResponse = exchangeCodeForToken(authResult.code(), authResult.codeVerifier());
            var claims = SignedJWT.parse((String) tokenResponse.get("access_token")).getJWTClaimsSet();

            // then
            assertThat(claims.getStringClaim("provider")).isEqualTo("google");
            assertThat(claims.getStringClaim("role")).isEqualTo("member");
            assertThat(claims.getClaim("household_id")).isNull();
        }
    }

    private record AuthorizationResult(String code, String codeVerifier) {}


    /**
     * Simulates the full social login + PKCE authorization code flow:
     * 1. Hit /oauth2/authorize to register the PKCE intent (redirected to /login)
     * 2. Authenticate via the Dex password form
     * 3. Spring callback exchanges the code, JIT provisioning runs
     * 4. Re-hit /oauth2/authorize; Spring issues the AS authorization code
     */
    private AuthorizationResult performSocialLogin(String email, String password) throws Exception {
        var cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        var codeVerifier = generateCodeVerifier();
        var authorizeUri = buildAuthorizeUri(generateCodeChallenge(codeVerifier));

        // Initiate AS flow; Spring saves the request and redirects to /login
        httpClient.send(buildGet(authorizeUri), HttpResponse.BodyHandlers.discarding());

        // Authenticate via Dex; returns the Spring OAuth2 callback URL
        var springCallbackUrl = authenticateWithDex(httpClient, email, password);

        // Spring exchanges the code with Dex and runs JIT provisioning
        httpClient.send(buildGet(springCallbackUrl), HttpResponse.BodyHandlers.discarding());

        // Authenticated user hits AS authorize; Spring issues the authorization code
        var codeRedirect = httpClient.send(buildGet(authorizeUri), HttpResponse.BodyHandlers.discarding());
        return new AuthorizationResult(
                extractQueryParam(codeRedirect.headers().firstValue("Location").orElseThrow(), "code"),
                codeVerifier);
    }

    /**
     * Initiates a social login without prior AS authorize, then returns the Spring redirect Location.
     * Used for error cases (e.g. email collision) where the flow ends at the callback.
     */
    private String attemptSocialLoginAndGetError(String email, String password) throws Exception {
        var cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        var springCallbackUrl = authenticateWithDex(httpClient, email, password);

        // JIT provisioning throws email_already_registered → Spring redirects to /login?error=...
        var errorResponse = httpClient.send(buildGet(springCallbackUrl), HttpResponse.BodyHandlers.discarding());
        return errorResponse.headers().firstValue("Location")
                .orElseThrow(() -> new AssertionError("Expected error redirect after callback"));
    }

    /**
     * Drives the Dex password-form login flow and returns the Spring OAuth2 callback URL.
     * Dex uses a multi-step redirect chain before serving the login form, so all 3xx
     * responses are followed manually until the form URL (200) is reached.
     */
    private String authenticateWithDex(HttpClient httpClient, String email, String password) throws Exception {
        var googleInitResponse = httpClient.send(
                buildGet("http://localhost:" + port + "/oauth2/authorization/google"),
                HttpResponse.BodyHandlers.discarding());

        var dexAuthUrl = googleInitResponse.headers().firstValue("Location")
                .orElseThrow(() -> new AssertionError("Expected redirect from /oauth2/authorization/google"));

        var currentUrl = dexAuthUrl;
        for (int hops = 0; hops < 5; hops++) {
            var response = httpClient.send(buildGet(currentUrl), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 3) break;
            var location = response.headers().firstValue("Location");
            if (location.isEmpty()) throw new AssertionError("Redirect without Location at " + currentUrl);
            currentUrl = resolveUrl(currentUrl, location.get());
        }
        var loginFormUrl = currentUrl;

        var formData = "login=" + URLEncoder.encode(email, UTF_8)
                + "&password=" + URLEncoder.encode(password, UTF_8);
        var callbackResponse = httpClient.send(buildPost(loginFormUrl, formData),
                HttpResponse.BodyHandlers.discarding());
        return callbackResponse.headers().firstValue("Location")
                .orElseThrow(() -> new AssertionError("No redirect after Dex login POST (url=" + loginFormUrl + ")"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exchangeCodeForToken(String code, String codeVerifier) {
        var body = "grant_type=authorization_code"
                + "&code=" + URLEncoder.encode(code, UTF_8)
                + "&redirect_uri=" + URLEncoder.encode("http://localhost:5173/callback", UTF_8)
                + "&client_id=librehousehold-spa"
                + "&code_verifier=" + URLEncoder.encode(codeVerifier, UTF_8);
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build()
                .post().uri("/oauth2/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    private HttpRequest buildGet(String uri) {
        return HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Accept", "text/html")
                .GET()
                .build();
    }

    private HttpRequest buildPost(String uri, String formData) {
        return HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();
    }

    private String buildAuthorizeUri(String codeChallenge) {
        return "http://localhost:" + port
                + "/oauth2/authorize?response_type=code"
                + "&client_id=librehousehold-spa"
                + "&redirect_uri=" + URLEncoder.encode("http://localhost:5173/callback", UTF_8)
                + "&scope=openid"
                + "&code_challenge=" + codeChallenge
                + "&code_challenge_method=S256";
    }

    private String generateCodeVerifier() {
        var bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateCodeChallenge(String codeVerifier) throws Exception {
        var bytes = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String resolveUrl(String baseUrl, String location) {
        var locationUri = URI.create(location);
        if (locationUri.isAbsolute()) {
            return location;
        }
        return URI.create(baseUrl).resolve(locationUri).toString();
    }

    private String extractQueryParam(String url, String name) {
        var query = URI.create(url).getQuery();
        if (query == null) throw new IllegalArgumentException("No query in: " + url);
        for (var param : query.split("&")) {
            var parts = param.split("=", 2);
            if (parts.length == 2 && parts[0].equals(name)) {
                return parts[1];
            }
        }
        throw new IllegalArgumentException("Param '" + name + "' not found in: " + url);
    }
}