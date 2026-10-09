package eu.wiegandt.librehousehold.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;

import static org.assertj.core.api.Assertions.assertThat;

class CommonProviderTest {

    private final CommonProvider commonProvider = new CommonProvider();

    @Nested
    class isCommonProvider {

        @ParameterizedTest
        @ValueSource(strings = {"google", "github", "facebook", "okta"})
        void knownProviderName_true(String registrationId) {
            // when
            var result = commonProvider.isCommonProvider(registrationId);

            // then
            assertThat(result).isTrue();
        }

        @Test
        void mixedCaseKnownProviderName_true() {
            // when
            var result = commonProvider.isCommonProvider("GitHub");

            // then
            assertThat(result).isTrue();
        }

        @Test
        void unknownProviderName_false() {
            // when
            var result = commonProvider.isCommonProvider("keycloak-self-hosted");

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    class find {

        @Test
        void knownProviderName_matchingCommonOAuth2Provider() {
            // when
            var result = commonProvider.find("google");

            // then
            assertThat(result).contains(CommonOAuth2Provider.GOOGLE);
        }

        @Test
        void mixedCaseKnownProviderName_matchingCommonOAuth2Provider() {
            // when
            var result = commonProvider.find("GitHub");

            // then
            assertThat(result).contains(CommonOAuth2Provider.GITHUB);
        }

        @Test
        void unknownProviderName_empty() {
            // when
            var result = commonProvider.find("keycloak-self-hosted");

            // then
            assertThat(result).isEmpty();
        }
    }
}
