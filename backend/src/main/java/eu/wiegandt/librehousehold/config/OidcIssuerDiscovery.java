package eu.wiegandt.librehousehold.config;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.stereotype.Component;

/**
 * Adapter to an external OIDC issuer's discovery endpoint: performs the network call behind
 * {@link ClientRegistrations#fromIssuerLocation}, so callers depend on an injectable collaborator
 * instead of a static method doing network I/O.
 */
@Component
public class OidcIssuerDiscovery {

    public ClientRegistration.Builder discover(String issuerUri) {
        return ClientRegistrations.fromIssuerLocation(issuerUri);
    }
}
