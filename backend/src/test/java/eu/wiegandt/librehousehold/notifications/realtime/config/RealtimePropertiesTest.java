package eu.wiegandt.librehousehold.notifications.realtime.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimePropertiesTest {

    private static final String SESSION_TIMEOUT = "server.servlet.session.timeout=30m";

    @Test
    void bind_emitterTimeoutBelowSessionTimeout_contextStarts() {
        // given
        var runner = new ApplicationContextRunner()
                .withInitializer(RealtimePropertiesTest::useApplicationConversionService)
                .withUserConfiguration(RealtimeConfiguration.class)
                .withPropertyValues(SESSION_TIMEOUT, "librehousehold.notifications.realtime.emitter-timeout=29m");

        // when / then
        runner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void bind_emitterTimeoutEqualToSessionTimeout_startupFails() {
        // given
        var runner = new ApplicationContextRunner()
                .withInitializer(RealtimePropertiesTest::useApplicationConversionService)
                .withUserConfiguration(RealtimeConfiguration.class)
                .withPropertyValues(SESSION_TIMEOUT, "librehousehold.notifications.realtime.emitter-timeout=30m");

        // when / then
        runner.run(context -> assertThat(context).getFailure()
                .rootCause().hasMessageContaining("emitter timeout must be shorter than the session timeout"));
    }

    // SpringApplication registers it in the application; it converts e.g. "30m" into a Duration
    private static void useApplicationConversionService(ConfigurableApplicationContext context) {
        context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RealtimeProperties.class)
    static class RealtimeConfiguration {
    }
}
