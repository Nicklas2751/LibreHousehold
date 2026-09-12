package eu.wiegandt.librehousehold.notifications.internal;

import eu.wiegandt.librehousehold.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the self-hoster override mechanism (see {@code librehousehold.notifications.templates.override-dir})
 * described in {@link EmailTemplatingConfig}: an override file for a key/locale takes precedence
 * over the bundled default, while a key/locale not present in the override directory still falls
 * back to the bundled default via {@code MessageSource#setParentMessageSource}. Only the English
 * message and the "verification" template are overridden here; German is deliberately left
 * un-overridden to prove the fallback path in the same test run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "librehousehold.security.oauth2-client.client-secret=test-client-secret")
@Import(TestcontainersConfiguration.class)
class EmailTemplatingConfigOverrideIT {

    @TempDir
    static Path overrideDir;

    @DynamicPropertySource
    static void overrideDirectory(DynamicPropertyRegistry registry) throws IOException {
        var overrideMessagesDir = Files.createDirectories(overrideDir.resolve("messages"));
        Files.writeString(overrideMessagesDir.resolve("email_en.properties"), "verification.subject=Overridden subject\n");

        var overrideTemplatesDir = Files.createDirectories(overrideDir.resolve("templates").resolve("email"));
        Files.writeString(overrideTemplatesDir.resolve("verification.html"),
                "<!DOCTYPE html><html><body><span>Override template marker</span></body></html>");

        registry.add("librehousehold.notifications.templates.override-dir", overrideDir::toString);
    }

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Test
    void messageSource_overriddenEnglishKey_returnsOverriddenValue() {
        // given
        // email_en.properties in the override directory (see overrideDirectory()) overrides "verification.subject"

        // when
        var resolvedValue = messageSource.getMessage("verification.subject", null, Locale.ENGLISH);

        // then
        assertThat(resolvedValue).isEqualTo("Overridden subject");
    }

    @Test
    void messageSource_nonOverriddenGermanKey_fallsBackToBundledDefault() {
        // given
        // no email_de.properties exists in the override directory (see overrideDirectory())

        // when
        var resolvedValue = messageSource.getMessage("verification.subject", null, Locale.GERMAN);

        // then
        assertThat(resolvedValue).isEqualTo("Bestätige deine E-Mail-Adresse");
    }

    @Test
    void templateEngine_overriddenVerificationTemplate_rendersOverriddenTemplate() {
        // given
        var context = new Context(Locale.ENGLISH);

        // when
        var renderedHtml = templateEngine.process("verification", context);

        // then
        assertThat(renderedHtml).contains("Override template marker");
    }
}
