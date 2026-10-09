package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.config.SocialLoginProperties.ProviderConfig;
import eu.wiegandt.librehousehold.config.SocialLoginProperties.ProviderType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocialLoginConfigurationValidatorTest {

    private static final String AUTHORIZATION_URI = "https://git.example.com/login/oauth/authorize";
    private static final String TOKEN_URI = "https://git.example.com/login/oauth/access_token";
    private static final String USER_INFO_URI = "https://git.example.com/api/v1/user";
    private static final String USER_NAME_ATTRIBUTE = "login";

    private final SocialLoginConfigurationValidator validator = new SocialLoginConfigurationValidator(new CommonProvider());

    @Nested
    class validate {

        @Test
        void socialLoginDisabledWithInvalidProvider_doesNotThrow() {
            // given
            var properties = new SocialLoginProperties(false, Map.of("keycloak-self-hosted",
                    new ProviderConfig(ProviderType.OIDC, null, "client-id", "client-secret", "Keycloak",
                            null, null, null, null)));

            // when / then
            assertThatCode(() -> validator.validate(properties)).doesNotThrowAnyException();
        }

        @Test
        void oidcProviderWithoutIssuerUriAndUnknownName_throwsIllegalStateException() {
            // given
            var properties = new SocialLoginProperties(true, Map.of("keycloak-self-hosted",
                    new ProviderConfig(ProviderType.OIDC, null, "client-id", "client-secret", "Keycloak",
                            null, null, null, null)));

            // when / then
            assertThatThrownBy(() -> validator.validate(properties))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("keycloak-self-hosted");
        }

        @Test
        void oidcProviderWithBlankIssuerUriAndUnknownName_throwsIllegalStateException() {
            // given
            var properties = new SocialLoginProperties(true, Map.of("keycloak-self-hosted",
                    new ProviderConfig(ProviderType.OIDC, " ", "client-id", "client-secret", "Keycloak",
                            null, null, null, null)));

            // when / then
            assertThatThrownBy(() -> validator.validate(properties))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContainingAll("keycloak-self-hosted", "issuer-uri");
        }

        @Test
        void oidcProviderWithoutIssuerUriAndCommonProviderName_doesNotThrow() {
            // given
            var properties = new SocialLoginProperties(true, Map.of("google",
                    new ProviderConfig(ProviderType.OIDC, null, "client-id", "client-secret", "Google",
                            null, null, null, null)));

            // when / then
            assertThatCode(() -> validator.validate(properties)).doesNotThrowAnyException();
        }

        @Test
        void oidcProviderWithIssuerUriAndUnknownName_doesNotThrow() {
            // given
            var properties = new SocialLoginProperties(true, Map.of("keycloak-self-hosted",
                    new ProviderConfig(ProviderType.OIDC, "https://keycloak.example.com/realms/household",
                            "client-id", "client-secret", "Keycloak", null, null, null, null)));

            // when / then
            assertThatCode(() -> validator.validate(properties)).doesNotThrowAnyException();
        }

        @Test
        void oauth2ProviderWithCommonProviderNameWithoutEndpoints_doesNotThrow() {
            // given
            var properties = new SocialLoginProperties(true, Map.of("github",
                    new ProviderConfig(ProviderType.OAUTH2, null, "client-id", "client-secret", "GitHub",
                            null, null, null, null)));

            // when / then
            assertThatCode(() -> validator.validate(properties)).doesNotThrowAnyException();
        }

        @Test
        void oauth2ProviderWithUnknownNameAndAllEndpoints_doesNotThrow() {
            // given
            var properties = new SocialLoginProperties(true, Map.of("forgejo",
                    new ProviderConfig(ProviderType.OAUTH2, null, "client-id", "client-secret", "Forgejo",
                            AUTHORIZATION_URI, TOKEN_URI, USER_INFO_URI, USER_NAME_ATTRIBUTE)));

            // when / then
            assertThatCode(() -> validator.validate(properties)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @MethodSource("eu.wiegandt.librehousehold.config.SocialLoginConfigurationValidatorTest#oauth2ProvidersMissingOrBlankOneField")
        void oauth2ProviderWithUnknownNameAndMissingOrBlankField_throwsIllegalStateExceptionNamingProviderAndField(
                ProviderConfig config, String missingField) {
            // given
            var properties = new SocialLoginProperties(true, Map.of("forgejo", config));

            // when / then
            assertThatThrownBy(() -> validator.validate(properties))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContainingAll("forgejo", missingField);
        }

        @Test
        void oauth2ProviderWithUnknownNameWithoutEndpoints_throwsIllegalStateExceptionNamingAllMissingFields() {
            // given
            var properties = new SocialLoginProperties(true, Map.of("forgejo",
                    new ProviderConfig(ProviderType.OAUTH2, null, "client-id", "client-secret", "Forgejo",
                            null, null, null, null)));

            // when / then
            assertThatThrownBy(() -> validator.validate(properties))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContainingAll("forgejo", "authorization-uri", "token-uri", "user-info-uri",
                            "user-name-attribute");
        }

        @Test
        void providerWithoutTypeAndUnknownName_throwsIllegalStateExceptionNamingProviderAndType() {
            // given
            var properties = new SocialLoginProperties(true, Map.of("forgejo",
                    new ProviderConfig(null, null, "client-id", "client-secret", "Forgejo",
                            AUTHORIZATION_URI, TOKEN_URI, USER_INFO_URI, USER_NAME_ATTRIBUTE)));

            // when / then
            assertThatThrownBy(() -> validator.validate(properties))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContainingAll("forgejo", "type");
        }

        @Test
        void providerWithoutTypeAndCommonProviderName_doesNotThrow() {
            // given
            var properties = new SocialLoginProperties(true, Map.of("google",
                    new ProviderConfig(null, null, "client-id", "client-secret", "Google",
                            null, null, null, null)));

            // when / then
            assertThatCode(() -> validator.validate(properties)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @MethodSource("eu.wiegandt.librehousehold.config.SocialLoginConfigurationValidatorTest#providersMissingOneClientCredential")
        void providerWithMissingClientCredential_throwsIllegalStateExceptionNamingProviderAndField(
                String registrationId, ProviderConfig config, String missingField) {
            // given
            var properties = new SocialLoginProperties(true, Map.of(registrationId, config));

            // when / then
            assertThatThrownBy(() -> validator.validate(properties))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContainingAll(registrationId, missingField);
        }
    }

    static Stream<Arguments> providersMissingOneClientCredential() {
        return Stream.of(
                Arguments.of("forgejo", new ProviderConfig(ProviderType.OAUTH2, null, null, "client-secret", "Forgejo",
                        AUTHORIZATION_URI, TOKEN_URI, USER_INFO_URI, USER_NAME_ATTRIBUTE), "client-id"),
                Arguments.of("forgejo", new ProviderConfig(ProviderType.OAUTH2, null, " ", "client-secret", "Forgejo",
                        AUTHORIZATION_URI, TOKEN_URI, USER_INFO_URI, USER_NAME_ATTRIBUTE), "client-id"),
                Arguments.of("forgejo", new ProviderConfig(ProviderType.OAUTH2, null, "client-id", null, "Forgejo",
                        AUTHORIZATION_URI, TOKEN_URI, USER_INFO_URI, USER_NAME_ATTRIBUTE), "client-secret"),
                Arguments.of("forgejo", new ProviderConfig(ProviderType.OAUTH2, null, "client-id", "", "Forgejo",
                        AUTHORIZATION_URI, TOKEN_URI, USER_INFO_URI, USER_NAME_ATTRIBUTE), "client-secret"),
                Arguments.of("google", new ProviderConfig(ProviderType.OIDC, null, null, "client-secret", "Google",
                        null, null, null, null), "client-id"),
                Arguments.of("google", new ProviderConfig(ProviderType.OIDC, null, "", "client-secret", "Google",
                        null, null, null, null), "client-id"),
                Arguments.of("google", new ProviderConfig(ProviderType.OIDC, null, "client-id", null, "Google",
                        null, null, null, null), "client-secret"),
                Arguments.of("google", new ProviderConfig(ProviderType.OIDC, null, "client-id", " ", "Google",
                        null, null, null, null), "client-secret"));
    }

    static Stream<Arguments> oauth2ProvidersMissingOrBlankOneField() {
        return Stream.of(
                Arguments.of(new ProviderConfig(ProviderType.OAUTH2, null, "client-id", "client-secret", "Forgejo",
                        null, TOKEN_URI, USER_INFO_URI, USER_NAME_ATTRIBUTE), "authorization-uri"),
                Arguments.of(new ProviderConfig(ProviderType.OAUTH2, null, "client-id", "client-secret", "Forgejo",
                        AUTHORIZATION_URI, null, USER_INFO_URI, USER_NAME_ATTRIBUTE), "token-uri"),
                Arguments.of(new ProviderConfig(ProviderType.OAUTH2, null, "client-id", "client-secret", "Forgejo",
                        AUTHORIZATION_URI, TOKEN_URI, null, USER_NAME_ATTRIBUTE), "user-info-uri"),
                Arguments.of(new ProviderConfig(ProviderType.OAUTH2, null, "client-id", "client-secret", "Forgejo",
                        AUTHORIZATION_URI, TOKEN_URI, USER_INFO_URI, null), "user-name-attribute"),
                Arguments.of(new ProviderConfig(ProviderType.OAUTH2, null, "client-id", "client-secret", "Forgejo",
                        " ", TOKEN_URI, USER_INFO_URI, USER_NAME_ATTRIBUTE), "authorization-uri"),
                Arguments.of(new ProviderConfig(ProviderType.OAUTH2, null, "client-id", "client-secret", "Forgejo",
                        AUTHORIZATION_URI, "", USER_INFO_URI, USER_NAME_ATTRIBUTE), "token-uri"),
                Arguments.of(new ProviderConfig(ProviderType.OAUTH2, null, "client-id", "client-secret", "Forgejo",
                        AUTHORIZATION_URI, TOKEN_URI, " ", USER_NAME_ATTRIBUTE), "user-info-uri"),
                Arguments.of(new ProviderConfig(ProviderType.OAUTH2, null, "client-id", "client-secret", "Forgejo",
                        AUTHORIZATION_URI, TOKEN_URI, USER_INFO_URI, ""), "user-name-attribute"));
    }
}
