package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.auth.model.AccountEntity;
import eu.wiegandt.librehousehold.auth.repository.AccountRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.HttpClientErrorException;
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
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RefreshTokenIT {

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
        void withValidRefreshToken_returnsNewTokenPair() throws Exception {
            // given
            var email = uniqueEmail("valid");
            accountRepository.save(new AccountEntity(UUID.randomUUID(), email, passwordEncoder.encode("s3cret!")));
            var initialTokens = obtainInitialTokenPair(email, "s3cret!");
            var refreshToken = (String) initialTokens.get("refresh_token");

            // when
            var refreshedTokens = exchangeRefreshToken(refreshToken);

            // then
            assertThat(refreshedTokens).containsKeys("access_token", "refresh_token");
            assertThat((String) refreshedTokens.get("refresh_token")).isNotEqualTo(refreshToken);
        }

        @Test
        void withConsumedRefreshToken_returnsInvalidGrant() throws Exception {
            // given
            var email = uniqueEmail("consumed");
            accountRepository.save(new AccountEntity(UUID.randomUUID(), email, passwordEncoder.encode("s3cret!")));
            var initialTokens = obtainInitialTokenPair(email, "s3cret!");
            var refreshToken = (String) initialTokens.get("refresh_token");
            exchangeRefreshToken(refreshToken); // consume RT1

            // when / then
            assertThatThrownBy(() -> exchangeRefreshToken(refreshToken))
                    .isInstanceOf(HttpClientErrorException.BadRequest.class)
                    .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getResponseBodyAsString())
                            .contains("invalid_grant"));
        }

        @Test
        void withReplayedConsumedRefreshToken_revokesAllTokensAndReturnsInvalidGrant() throws Exception {
            // given
            var email = uniqueEmail("replay");
            accountRepository.save(new AccountEntity(UUID.randomUUID(), email, passwordEncoder.encode("s3cret!")));
            var initialTokens = obtainInitialTokenPair(email, "s3cret!");
            var refreshToken1 = (String) initialTokens.get("refresh_token");
            var secondTokens = exchangeRefreshToken(refreshToken1); // consume RT1, get RT2
            var refreshToken2 = (String) secondTokens.get("refresh_token");

            // when — replay the already-consumed RT1 to trigger family revocation
            assertThatThrownBy(() -> exchangeRefreshToken(refreshToken1))
                    .isInstanceOf(HttpClientErrorException.BadRequest.class);

            // then — RT2 is also revoked (family revocation)
            assertThatThrownBy(() -> exchangeRefreshToken(refreshToken2))
                    .isInstanceOf(HttpClientErrorException.BadRequest.class)
                    .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getResponseBodyAsString())
                            .contains("invalid_grant"));
        }
    }

    private String uniqueEmail(String scenario) {
        return "refresh-" + scenario + "-" + UUID.randomUUID() + "@example.com";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> obtainInitialTokenPair(String email, String password) throws Exception {
        var cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        var codeVerifier = generateCodeVerifier();
        var codeChallenge = generateCodeChallenge(codeVerifier);
        var authorizeUri = buildAuthorizeUri(codeChallenge);

        httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(authorizeUri)).GET()
                        .header("Accept", "text/html").build(),
                HttpResponse.BodyHandlers.discarding());

        var loginPageResponse = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + "/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        var csrfToken = extractCsrf(loginPageResponse.body());

        var loginBody = "username=" + URLEncoder.encode(email, UTF_8)
                + "&password=" + URLEncoder.encode(password, UTF_8)
                + "&_csrf=" + URLEncoder.encode(csrfToken, UTF_8);
        httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/login"))
                        .POST(HttpRequest.BodyPublishers.ofString(loginBody))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .build(),
                HttpResponse.BodyHandlers.discarding());

        var codeRedirect = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(authorizeUri)).GET()
                        .header("Accept", "text/html").build(),
                HttpResponse.BodyHandlers.discarding());

        var location = codeRedirect.headers().firstValue("Location").orElseThrow();
        var code = extractQueryParam(location, "code");

        var tokenBody = "grant_type=authorization_code"
                + "&code=" + URLEncoder.encode(code, UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, UTF_8)
                + "&client_id=" + CLIENT_ID
                + "&code_verifier=" + URLEncoder.encode(codeVerifier, UTF_8);

        return RestClient.create("http://localhost:" + port)
                .post()
                .uri("/oauth2/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(tokenBody)
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> exchangeRefreshToken(String refreshToken) {
        var body = "grant_type=refresh_token"
                + "&refresh_token=" + URLEncoder.encode(refreshToken, UTF_8)
                + "&client_id=" + CLIENT_ID;
        return RestClient.create("http://localhost:" + port)
                .post()
                .uri("/oauth2/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(body)
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

    private String extractCsrf(String html) {
        var matcher = Pattern.compile("<input[^>]*name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        if (matcher.find()) return matcher.group(1);
        matcher = Pattern.compile("<input[^>]*value=\"([^\"]+)\"[^>]*name=\"_csrf\"").matcher(html);
        return matcher.find() ? matcher.group(1) : "";
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
