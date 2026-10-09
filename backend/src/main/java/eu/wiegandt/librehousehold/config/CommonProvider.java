package eu.wiegandt.librehousehold.config;

import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * Knows which social-login provider names Spring Security ships defaults for
 * ({@link CommonOAuth2Provider}, e.g. {@code google}, {@code github}). Matching is
 * case-insensitive, because configuration keys are lower-case while the enum constants are
 * upper-case.
 */
@Component
public class CommonProvider {

    public boolean isCommonProvider(String registrationId) {
        return find(registrationId).isPresent();
    }

    public Optional<CommonOAuth2Provider> find(String registrationId) {
        try {
            return Optional.of(CommonOAuth2Provider.valueOf(registrationId.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }
}
