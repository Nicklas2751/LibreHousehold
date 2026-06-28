package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.model.AuthProviders;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AuthProviderServiceTest {

    // InMemoryClientRegistrationRepository implements both interfaces; tests use this combined type
    interface IterableClientRegistrationRepository
            extends ClientRegistrationRepository, Iterable<ClientRegistration> {}

    @Mock
    ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    @Nested
    class getAvailableProviders {

        @Test
        void noRepositoryConfigured_returnsSocialProvidersEmpty() {
            // given
            doReturn(null).when(clientRegistrations).getIfAvailable();
            var service = new AuthProviderService(new AuthProviderProperties(true), clientRegistrations);

            // when
            var result = service.getAvailableProviders();

            // then
            var expected = new AuthProviders().local(true).socialProviders(List.of());
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void repositoryWithTwoRegistrations_returnsTheirIds() {
            // given
            var reg1 = mock(ClientRegistration.class);
            var reg2 = mock(ClientRegistration.class);
            doReturn("github").when(reg1).getRegistrationId();
            doReturn("keycloak").when(reg2).getRegistrationId();
            var repository = mock(IterableClientRegistrationRepository.class);
            doReturn(List.of(reg1, reg2).iterator()).when(repository).iterator();
            doReturn(repository).when(clientRegistrations).getIfAvailable();
            var service = new AuthProviderService(new AuthProviderProperties(true), clientRegistrations);

            // when
            var result = service.getAvailableProviders();

            // then
            assertThat(result.getSocialProviders()).containsExactlyInAnyOrder("github", "keycloak");
        }

        @Test
        void localFalse_returnsLocalFalse() {
            // given
            doReturn(null).when(clientRegistrations).getIfAvailable();
            var service = new AuthProviderService(new AuthProviderProperties(false), clientRegistrations);

            // when
            var result = service.getAvailableProviders();

            // then
            assertThat(result.getLocal()).isFalse();
        }
    }
}
