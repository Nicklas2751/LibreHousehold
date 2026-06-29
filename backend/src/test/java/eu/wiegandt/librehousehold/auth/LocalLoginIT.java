package eu.wiegandt.librehousehold.auth;

import com.nimbusds.jwt.SignedJWT;
import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.auth.model.AccountEntity;
import eu.wiegandt.librehousehold.auth.repository.AccountRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

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

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LocalLoginIT {

    private static final String REDIRECT_URI = "http://localhost:5173/callback";
    private static final String CLIENT_ID = "librehousehold-spa";

    @LocalServerPort
    int port;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Nested
    class postToken {

        @Test
        void withValidAuthorizationCode_returnsAccessToken() throws Exception {
            // given
            var email = "token-test-" + UUID.randomUUID() + "@example.com";
            accountRepository.save(new AccountEntity(UUID.randomUUID(), email, passwordEncoder.encode("s3cret!")));
            var result = obtainAuthorizationCode(email, "s3cret!");

            // when
            var tokenResponse = exchangeCodeForToken(result.code(), result.codeVerifier());

            // then
            assertThat(tokenResponse).containsKey("access_token");
        }

        @Test
        void accessToken_containsExpectedClaims() throws Exception {
            // given
            var email = "claims-test-" + UUID.randomUUID() + "@example.com";
            accountRepository.save(new AccountEntity(UUID.randomUUID(), email, passwordEncoder.encode("s3cret!")));
            var result = obtainAuthorizationCode(email, "s3cret!");
            var tokenResponse = exchangeCodeForToken(result.code(), result.codeVerifier());

            // when
            var accessToken = (String) tokenResponse.get("access_token");
            var claims = SignedJWT.parse(accessToken).getJWTClaimsSet();

            // then — no household exists for this account, so household_id is null
            assertThat(claims.getStringClaim("role")).isEqualTo("member");
            assertThat(claims.getStringClaim("provider")).isEqualTo("local");
            assertThat(claims.getClaim("household_id")).isNull();
        }
    }

    private record AuthorizationResult(String code, String codeVerifier) {}

    private AuthorizationResult obtainAuthorizationCode(String email, String password) throws Exception {
        var cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        var codeVerifier = generateCodeVerifier();
        var codeChallenge = generateCodeChallenge(codeVerifier);
        var authorizeUri = buildAuthorizeUri(codeChallenge);

        // Step 1: Trigger the authorization flow; Spring saves the request and redirects to /login
        httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(authorizeUri)).GET()
                        .header("Accept", "text/html").build(),
                HttpResponse.BodyHandlers.discarding());

        // Step 2: Simulate the SPA's first backend call; SpaCsrfTokenRequestHandler writes the XSRF-TOKEN cookie
        httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/v1/auth/providers"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.discarding());

        // Step 3: Read the XSRF-TOKEN cookie set by Spring Security's SpaCsrfTokenRequestHandler
        var csrfToken = extractXsrfTokenFromCookies(cookieManager);

        // Step 4: Submit credentials; Spring authenticates and redirects to the saved /oauth2/authorize URL
        var loginBody = "username=" + URLEncoder.encode(email, UTF_8)
                + "&password=" + URLEncoder.encode(password, UTF_8);
        httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/login"))
                        .POST(HttpRequest.BodyPublishers.ofString(loginBody))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("X-XSRF-TOKEN", csrfToken)
                        .build(),
                HttpResponse.BodyHandlers.discarding());

        // Step 5: Hit /oauth2/authorize again; Spring issues the code and redirects to the callback
        var codeRedirect = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(authorizeUri)).GET()
                        .header("Accept", "text/html").build(),
                HttpResponse.BodyHandlers.discarding());

        var location = codeRedirect.headers().firstValue("Location").orElseThrow();
        return new AuthorizationResult(extractQueryParam(location, "code"), codeVerifier);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exchangeCodeForToken(String code, String codeVerifier) {
        var tokenBody = "grant_type=authorization_code"
                + "&code=" + URLEncoder.encode(code, UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, UTF_8)
                + "&client_id=" + CLIENT_ID
                + "&code_verifier=" + URLEncoder.encode(codeVerifier, UTF_8);

        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build()
                .post()
                .uri("/oauth2/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(tokenBody)
                .retrieve()
                .body(Map.class);
    }

    private String buildAuthorizeUri(String codeChallenge) {
        return "http://localhost:" + port
                + "/oauth2/authorize?response_type=code"
                + "&client_id=" + CLIENT_ID
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, UTF_8)
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
        var digest = MessageDigest.getInstance("SHA-256");
        var bytes = digest.digest(codeVerifier.getBytes(US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String extractXsrfTokenFromCookies(CookieManager cookieManager) {
        return cookieManager.getCookieStore().getCookies().stream()
                .filter(c -> "XSRF-TOKEN".equals(c.getName()))
                .map(java.net.HttpCookie::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("XSRF-TOKEN cookie not set by Spring Security"));
    }

    private String extractQueryParam(String url, String name) {
        var query = URI.create(url).getQuery();
        for (var param : query.split("&")) {
            var parts = param.split("=", 2);
            if (parts.length == 2 && parts[0].equals(name)) {
                return parts[1];
            }
        }
        throw new IllegalArgumentException("Param '" + name + "' not found in: " + url);
    }
}
