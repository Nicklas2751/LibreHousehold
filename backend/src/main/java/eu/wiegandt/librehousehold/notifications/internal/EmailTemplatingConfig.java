package eu.wiegandt.librehousehold.notifications.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

import java.nio.charset.StandardCharsets;

/**
 * Configures email templating standalone (not through {@code ThymeleafAutoConfiguration}, which
 * targets Spring MVC view resolution): resolves both translated texts and HTML layout from a
 * bundled classpath default, with an optional self-hoster override directory taking precedence
 * (see {@code librehousehold.notifications.templates.override-dir}).
 */
@Configuration
public class EmailTemplatingConfig {

    private static final String BUNDLED_MESSAGES_BASENAME = "classpath:messages/email";
    private static final String BUNDLED_TEMPLATES_PREFIX = "templates/email/";
    private static final String OVERRIDE_TEMPLATES_SUBDIR = "/templates/email/";
    private static final String OVERRIDE_MESSAGES_SUBDIR = "/messages/email";
    private static final String TEMPLATE_SUFFIX = ".html";

    // @Primary: Boot's MessageSourceAutoConfiguration always registers its own default-named
    // "messageSource" bean (basename "messages") regardless of this bean's presence, so without a
    // tie-breaker, any plain "MessageSource" injection point risks resolving to that unrelated,
    // email-agnostic bean instead of this one.
    @Primary
    @Bean
    public MessageSource emailMessageSource(@Value("${librehousehold.notifications.templates.override-dir}") String overrideDir) {
        var bundledMessageSource = bundledMessageSource();
        if (overrideDir.isBlank()) {
            return bundledMessageSource;
        }

        var overrideMessageSource = new ReloadableResourceBundleMessageSource();
        overrideMessageSource.setBasename("file:" + overrideDir + OVERRIDE_MESSAGES_SUBDIR);
        overrideMessageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        overrideMessageSource.setParentMessageSource(bundledMessageSource);
        return overrideMessageSource;
    }

    @Bean
    public SpringTemplateEngine emailTemplateEngine(MessageSource emailMessageSource,
            @Value("${librehousehold.notifications.templates.override-dir}") String overrideDir) {
        var templateEngine = new SpringTemplateEngine();
        if (!overrideDir.isBlank()) {
            templateEngine.addTemplateResolver(overrideTemplateResolver(overrideDir));
        }
        templateEngine.addTemplateResolver(bundledTemplateResolver());
        templateEngine.setTemplateEngineMessageSource(emailMessageSource);
        return templateEngine;
    }

    private ReloadableResourceBundleMessageSource bundledMessageSource() {
        var messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename(BUNDLED_MESSAGES_BASENAME);
        // Properties files (e.g. email_de.properties) are authored as plain UTF-8 text with real
        // umlauts rather than \\uXXXX escapes; without this, DefaultPropertiesPersister falls back
        // to ISO-8859-1 and umlauts/ß would be resolved as mojibake.
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        return messageSource;
    }

    private ITemplateResolver overrideTemplateResolver(String overrideDir) {
        var templateResolver = new FileTemplateResolver();
        templateResolver.setOrder(1);
        templateResolver.setPrefix(overrideDir + OVERRIDE_TEMPLATES_SUBDIR);
        templateResolver.setSuffix(TEMPLATE_SUFFIX);
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        templateResolver.setCheckExistence(true);
        return templateResolver;
    }

    private ITemplateResolver bundledTemplateResolver() {
        var templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setOrder(2);
        templateResolver.setPrefix(BUNDLED_TEMPLATES_PREFIX);
        templateResolver.setSuffix(TEMPLATE_SUFFIX);
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        return templateResolver;
    }
}
