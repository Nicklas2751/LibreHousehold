package eu.wiegandt.librehousehold.notifications.internal;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test proving that {@link EmailTemplatingConfig}'s bundled-default configuration (no
 * override directory set) actually resolves messages and renders templates, using the real
 * "verification" key/template as a stand-in for any bundled default. Self-hoster override
 * behavior is verified separately in {@link EmailTemplatingConfigOverrideIT}: mixing both here
 * would require a single {@code @DynamicPropertySource} shared by all test methods in the class,
 * which would force the override directory to be active for these bundled-default assertions too.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "librehousehold.security.oauth2-client.client-secret=test-client-secret")
@Import(TestcontainersConfiguration.class)
class EmailTemplatingConfigIT {

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private SpringTemplateEngine templateEngine;

    @ParameterizedTest
    @CsvSource({
            "en, Verify your email address",
            "de, Bestätige deine E-Mail-Adresse"
    })
    void messageSource_verificationSubjectKey_resolvesLocalizedValue(String languageTag, String expectedValue) {
        // given
        var locale = Locale.forLanguageTag(languageTag);

        // when
        var resolvedValue = messageSource.getMessage("verification.subject", null, locale);

        // then
        assertThat(resolvedValue).isEqualTo(expectedValue);
    }

    @Test
    void templateEngine_verificationTemplate_rendersVerificationLink() {
        // given
        var verificationLink = "https://example.com/link";
        var context = new Context(Locale.ENGLISH, Map.of("verificationLink", verificationLink));

        // when
        var renderedHtml = templateEngine.process("verification", context);

        // then
        assertThat(renderedHtml).contains(verificationLink);
    }
}
