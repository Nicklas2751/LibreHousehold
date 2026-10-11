package eu.wiegandt.librehousehold;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import wf.garnier.testcontainers.dexidp.DexContainer;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "librehousehold.security.oauth2-client.client-secret=test-client-secret")
@Import({TestcontainersConfiguration.class, DexTestcontainersConfiguration.class})
class DexTestcontainersConfigurationIT {

    private static final String REGISTRATION_ID = "dev-provider";

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private DexContainer dexContainer;

    @Test
    void findByRegistrationId_devProvider_registrationDiscoveredFromDexContainerWithDefaultOidcScopes() {
        // given
        var expected = new DevProviderRegistration(dexContainer.getIssuerUri(),
                DexTestcontainersConfiguration.CLIENT_ID, Set.of("openid", "profile", "email"));

        // when
        var registration = clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID);
        var result = new DevProviderRegistration(registration.getProviderDetails().getIssuerUri(),
                registration.getClientId(), registration.getScopes());

        // then
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    private record DevProviderRegistration(String issuerUri, String clientId, Set<String> scopes) {
    }
}
