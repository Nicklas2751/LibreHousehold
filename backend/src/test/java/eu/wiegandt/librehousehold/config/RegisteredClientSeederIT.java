package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {"librehousehold.security.oauth2-client.client-secret=test-client-secret"})
@Import(TestcontainersConfiguration.class)
class RegisteredClientSeederIT {

    private static final String REGISTERED_CLIENT_ID = "spa-backend-client";

    @Autowired
    private RegisteredClientSeeder seeder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seed_missingThenCalledTwice_insertsOnceThenNoOps() {
        // given — the ApplicationRunner already seeded the client once at context startup, so it
        // must be removed first: otherwise both seed() calls below would hit the no-op path only,
        // and the test would never actually exercise the insert path. The row is re-created by the
        // first seed() call below, so the table ends up in the same state it started in — no
        // @AfterEach cleanup needed.
        var redirectUri = "https://household.example.com/login/oauth2/code/spa-backend-client";
        jdbcTemplate.update("DELETE FROM oauth2_registered_client WHERE client_id = ?", REGISTERED_CLIENT_ID);

        // when
        seeder.seed(redirectUri);
        seeder.seed(redirectUri);

        // then
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oauth2_registered_client WHERE client_id = ?",
                Integer.class, REGISTERED_CLIENT_ID)).isEqualTo(1);
    }
}
