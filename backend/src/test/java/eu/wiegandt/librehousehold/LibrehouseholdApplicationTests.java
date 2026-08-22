package eu.wiegandt.librehousehold;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"librehousehold.security.oauth2-client.client-secret=test-client-secret"})
class LibrehouseholdApplicationTests {

    @Test
    void contextLoads() {
    }

}
