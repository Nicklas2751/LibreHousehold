package eu.wiegandt.librehousehold.config;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Adapter to GitHub's {@code /user/emails} endpoint: GitHub is no OIDC provider, and its user
 * endpoint reveals at most the public e-mail address, without saying whether it is verified.
 * Needs an access token with the {@code user:email} scope.
 */
@Component
public class GitHubEmailClient {

    private static final String EMAILS_URI = "https://api.github.com/user/emails";
    private static final MediaType GITHUB_JSON = MediaType.parseMediaType("application/vnd.github+json");

    private final RestClient restClient;

    public GitHubEmailClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public List<GitHubEmail> fetchEmails(String accessToken) {
        return restClient.get()
                .uri(EMAILS_URI)
                .accept(GITHUB_JSON)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public record GitHubEmail(String email, boolean primary, boolean verified) {
    }
}
