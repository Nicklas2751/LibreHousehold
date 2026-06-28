package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.model.AuthProviders;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
class AuthProviderService {

    private final AuthProviderProperties properties;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    AuthProviderService(AuthProviderProperties properties,
                        ObjectProvider<ClientRegistrationRepository> clientRegistrations) {
        this.properties = properties;
        this.clientRegistrations = clientRegistrations;
    }

    AuthProviders getAvailableProviders() {
        return new AuthProviders()
                .local(properties.local())
                .socialProviders(detectSocialProviders());
    }

    private List<String> detectSocialProviders() {
        var repository = clientRegistrations.getIfAvailable();
        if (!(repository instanceof Iterable<?> iterable)) {
            return List.of();
        }
        var ids = new ArrayList<String>();
        for (var obj : iterable) {
            if (obj instanceof ClientRegistration reg) {
                ids.add(reg.getRegistrationId());
            }
        }
        return List.copyOf(ids);
    }
}
