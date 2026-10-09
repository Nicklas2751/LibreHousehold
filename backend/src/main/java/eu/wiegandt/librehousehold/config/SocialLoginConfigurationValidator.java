package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.config.SocialLoginProperties.ProviderConfig;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Stream;

/**
 * Fails fast (see ADR-016) if {@code librehousehold.security.social-login.enabled} is
 * {@code true} but a configured provider is incomplete and could never resolve a working
 * {@code ClientRegistration}. Rules per provider:
 * <ul>
 *     <li>Every provider, including well-known {@link CommonProvider} names (e.g. {@code google}),
 *     needs a {@code client-id} and a {@code client-secret}, because LibreHousehold always acts as a
 *     confidential client toward the external provider.</li>
 *     <li>A well-known provider name needs nothing else; the name alone identifies the provider, so
 *     {@code type} may be absent.</li>
 *     <li>An unknown provider name needs a {@code type}. A {@code type: oidc} provider then needs
 *     an {@code issuer-uri}; a {@code type: oauth2} provider needs {@code authorization-uri},
 *     {@code token-uri}, {@code user-info-uri} and {@code user-name-attribute}.</li>
 * </ul>
 * A blank value counts as missing, since an unset environment variable behind a placeholder such
 * as {@code ${GOOGLE_CLIENT_ID:}} binds to an empty string rather than {@code null}. Consistent with
 * the existing fail-fast pattern for {@code oauth2-client.client-secret} (see {@code SecurityConfig}).
 */
@Component
public class SocialLoginConfigurationValidator {

    private final CommonProvider commonProvider;

    public SocialLoginConfigurationValidator(CommonProvider commonProvider) {
        this.commonProvider = commonProvider;
    }

    public void validate(SocialLoginProperties properties) {
        if (!properties.enabled()) {
            return;
        }
        properties.providers().forEach(this::validateProvider);
    }

    private void validateProvider(String registrationId, ProviderConfig config) {
        validateClientCredentials(registrationId, config);
        if (commonProvider.isCommonProvider(registrationId)) {
            return;
        }
        switch (config.type()) {
            case null -> throw new IllegalStateException("Social login provider '" + registrationId
                    + "' has no known provider name (e.g. google, github) and is missing: type.");
            case OIDC -> validateOidcProvider(registrationId, config);
            case OAUTH2 -> validateOAuth2Provider(registrationId, config);
        }
    }

    private void validateClientCredentials(String registrationId, ProviderConfig config) {
        var missingFields = findMissingFields(
                new RequiredField("client-id", config.clientId()),
                new RequiredField("client-secret", config.clientSecret()));
        if (!missingFields.isEmpty()) {
            throw new IllegalStateException("Social login provider '" + registrationId + "' is missing: "
                    + String.join(", ", missingFields) + ".");
        }
    }

    private void validateOidcProvider(String registrationId, ProviderConfig config) {
        if (!StringUtils.hasText(config.issuerUri())) {
            throw new IllegalStateException("Social login provider '" + registrationId
                    + "' is of type oidc but has neither an issuer-uri nor a known provider name "
                    + "(e.g. google, github) configured.");
        }
    }

    private void validateOAuth2Provider(String registrationId, ProviderConfig config) {
        var missingFields = findMissingFields(
                new RequiredField("authorization-uri", config.authorizationUri()),
                new RequiredField("token-uri", config.tokenUri()),
                new RequiredField("user-info-uri", config.userInfoUri()),
                new RequiredField("user-name-attribute", config.userNameAttribute()));
        if (!missingFields.isEmpty()) {
            throw new IllegalStateException("Social login provider '" + registrationId
                    + "' is of type oauth2 without a known provider name (e.g. google, github) but is missing: "
                    + String.join(", ", missingFields) + ".");
        }
    }

    private List<String> findMissingFields(RequiredField... fields) {
        return Stream.of(fields)
                .filter(RequiredField::isMissing)
                .map(RequiredField::propertyName)
                .toList();
    }

    private record RequiredField(String propertyName, String value) {

        boolean isMissing() {
            return !StringUtils.hasText(value);
        }
    }
}
