package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import eu.wiegandt.librehousehold.auth.repository.AccountRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LocalRegistrationIT {

    @LocalServerPort
    int port;

    @Autowired
    AccountRepository accountRepository;

    @Nested
    class registerLocal {

        @Test
        void withValidData_createsAccountAndHousehold() {
            // given
            var email = "register-" + UUID.randomUUID() + "@example.com";
            var requestBody = Map.of(
                    "householdName", "Test Haushalt",
                    "memberName", "Test Admin",
                    "email", email,
                    "password", "supersecret123"
            );

            // when
            var response = restClient()
                    .post()
                    .uri("/v1/auth/register")
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .toEntity(Map.class);

            // then
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsKey("inviteToken");
            assertThat(response.getBody()).containsKey("household");
            assertThat(accountRepository.findByEmail(email)).isPresent();
        }

        @Test
        void withDuplicateEmail_returns409() {
            // given
            var email = "duplicate-" + UUID.randomUUID() + "@example.com";
            var requestBody = Map.of(
                    "householdName", "Erster Haushalt",
                    "memberName", "Max",
                    "email", email,
                    "password", "supersecret123"
            );
            restClient().post().uri("/v1/auth/register")
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();

            // when
            var response = restClient()
                    .post()
                    .uri("/v1/auth/register")
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(), (req, res) -> {})
                    .toEntity(String.class);

            // then
            assertThat(response.getStatusCode().value()).isEqualTo(409);
        }
    }

    private RestClient restClient() {
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .build();
    }
}
