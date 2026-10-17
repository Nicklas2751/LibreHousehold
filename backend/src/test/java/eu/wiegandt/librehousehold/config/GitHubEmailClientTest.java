package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.config.GitHubEmailClient.GitHubEmail;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubEmailClientTest {

    @Nested
    class fetchEmails {

        @Test
        void accessToken_returnsEmailsOfAuthenticatedUser() {
            // given
            var restClientBuilder = RestClient.builder();
            var gitHub = MockRestServiceServer.bindTo(restClientBuilder).build();
            gitHub.expect(requestTo("https://api.github.com/user/emails"))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                    .andRespond(withSuccess("""
                            [
                              {"email": "octocat@github.com", "primary": true, "verified": true, "visibility": "public"},
                              {"email": "octocat@example.com", "primary": false, "verified": false, "visibility": null}
                            ]
                            """, MediaType.APPLICATION_JSON));
            var client = new GitHubEmailClient(restClientBuilder);
            var expected = List.of(
                    new GitHubEmail("octocat@github.com", true, true),
                    new GitHubEmail("octocat@example.com", false, false));

            // when
            var result = client.fetchEmails("access-token");

            // then
            assertThat(result).isEqualTo(expected);
        }
    }
}
