package eu.wiegandt.librehousehold.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import wf.garnier.testcontainers.dexidp.DexContainer;

import java.net.URI;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Drives the full Dex/PKCE social-login flow against a running instance for integration tests
 * (see {@link SocialLoginIT}, {@link SocialJoinIT}). Instantiate per test with the test's own
 * {@code RestTestClient} and server port; each instance is stateless between calls (the cookie jar
 * used to carry a session across the multi-hop redirect chain lives inside each method call).
 */
class SocialLoginFlow {

    private static final String REDIRECT_URI = "http://localhost:5173/callback";
    private static final String CLIENT_ID = "librehousehold-spa";

    private final RestTestClient restTestClient;
    private final int port;

    SocialLoginFlow(RestTestClient restTestClient, int port) {
        this.restTestClient = restTestClient;
        this.port = port;
    }

    record AuthorizationResult(String code, String codeVerifier) {
    }

    /**
     * Registers the dynamic Dex-issued OAuth2 provider properties for the "google" registration.
     * Call from each test class's own {@code @DynamicPropertySource} method, since that method must
     * reference a {@code @Container} field declared in the same class.
     */
    static void registerDexProperties(DynamicPropertyRegistry registry, DexContainer dex) {
        registry.add("spring.security.oauth2.client.provider.google.issuer-uri", dex::getIssuerUri);
        registry.add("spring.security.oauth2.client.provider.google.authorization-uri", () -> dex.getIssuerUri() + "/auth");
        registry.add("spring.security.oauth2.client.provider.google.token-uri", () -> dex.getIssuerUri() + "/token");
        registry.add("spring.security.oauth2.client.provider.google.jwk-set-uri", () -> dex.getIssuerUri() + "/keys");
        registry.add("spring.security.oauth2.client.provider.google.user-info-uri", () -> dex.getIssuerUri() + "/userinfo");
    }

    /**
     * Simulates the full social login + PKCE authorization code flow:
     * 1. Hit /oauth2/authorize to register the PKCE intent (redirected to /login)
     * 2. Authenticate via the Dex password form
     * 3. Spring callback exchanges the code, JIT provisioning runs
     * 4. Re-hit /oauth2/authorize; Spring issues the AS authorization code
     */
    AuthorizationResult performSocialLogin(String email, String password) {
        var cookies = new LinkedHashMap<String, String>();
        var codeVerifier = generateCodeVerifier();
        var authorizeUri = buildAuthorizeUri(generateCodeChallenge(codeVerifier));

        // Initiate AS flow; Spring saves the request and redirects to /login
        exchangeGet(cookies, authorizeUri);

        // Authenticate via Dex; returns the Spring OAuth2 callback URL
        var springCallbackUrl = authenticateWithDex(cookies, email, password);

        // Spring exchanges the code with Dex and runs JIT provisioning
        exchangeGet(cookies, springCallbackUrl);

        // Authenticated user hits AS authorize; Spring issues the authorization code
        var codeRedirect = exchangeGet(cookies, authorizeUri);
        var location = locationOf(codeRedirect);
        if (location == null) throw new AssertionError("Expected redirect with authorization code");
        return new AuthorizationResult(extractQueryParam(location, "code"), codeVerifier);
    }

    /**
     * Initiates a social login without prior AS authorize, then returns the Spring redirect Location.
     * Used for error cases (e.g. email collision) where the flow ends at the callback.
     */
    String attemptSocialLoginAndGetError(String email, String password) {
        var cookies = new LinkedHashMap<String, String>();
        var springCallbackUrl = authenticateWithDex(cookies, email, password);

        // JIT provisioning throws email_already_registered → Spring redirects to /login?error=...
        var errorResult = exchangeGet(cookies, springCallbackUrl);
        var location = locationOf(errorResult);
        if (location == null) throw new AssertionError("Expected error redirect after callback");
        return location;
    }

    /**
     * Runs {@link #performSocialLogin} and immediately exchanges the resulting code for an access
     * token, since join-completion is a Bearer-authenticated REST call, not a session-cookie one.
     */
    String performSocialLoginAndGetAccessToken(String email, String password) {
        var authResult = performSocialLogin(email, password);
        var tokenResponse = exchangeCodeForToken(authResult.code(), authResult.codeVerifier());
        return (String) tokenResponse.get("access_token");
    }

    /**
     * Drives the Dex password-form login flow and returns the Spring OAuth2 callback URL.
     * Dex uses a multi-step redirect chain before serving the login form, so all 3xx
     * responses are followed manually until the form URL (200) is reached.
     */
    private String authenticateWithDex(Map<String, String> cookies, String email, String password) {
        var currentUrl = "http://localhost:" + port + "/oauth2/authorization/google";
        for (int hops = 0; hops < 6; hops++) {
            var result = exchangeGet(cookies, currentUrl);
            if (result.getStatus().value() / 100 != 3) break;
            var location = locationOf(result);
            if (location == null) throw new AssertionError("Redirect without Location at " + currentUrl);
            currentUrl = resolveUrl(currentUrl, location);
        }
        var loginFormUrl = currentUrl;

        var formData = "login=" + URLEncoder.encode(email, UTF_8)
                + "&password=" + URLEncoder.encode(password, UTF_8);
        var result = restTestClient.post()
                .uri(URI.create(loginFormUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .cookies(c -> c.setAll(cookies))
                .body(formData)
                .exchange()
                .returnResult();
        updateCookies(cookies, result);
        var location = locationOf(result);
        if (location == null) {
            throw new AssertionError("No redirect after Dex login POST (url=" + loginFormUrl + ")");
        }
        return location;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> exchangeCodeForToken(String code, String codeVerifier) {
        var body = "grant_type=authorization_code"
                + "&code=" + URLEncoder.encode(code, UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, UTF_8)
                + "&client_id=" + CLIENT_ID
                + "&code_verifier=" + URLEncoder.encode(codeVerifier, UTF_8);
        return restTestClient.post()
                .uri("/oauth2/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
    }

    private ExchangeResult exchangeGet(Map<String, String> cookies, String uri) {
        var result = restTestClient.get()
                .uri(URI.create(uri))
                .header("Accept", "text/html")
                .cookies(c -> c.setAll(cookies))
                .exchange()
                .returnResult();
        updateCookies(cookies, result);
        return result;
    }

    private void updateCookies(Map<String, String> cookies, ExchangeResult result) {
        result.getResponseCookies().forEach((name, values) -> cookies.put(name, values.getFirst().getValue()));
    }

    private String locationOf(ExchangeResult result) {
        return result.getResponseHeaders().getFirst(HttpHeaders.LOCATION);
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

    private String generateCodeChallenge(String codeVerifier) {
        try {
            var bytes = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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