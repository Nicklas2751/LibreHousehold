package eu.wiegandt.librehousehold.realtime.config;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;

/**
 * Created by Spring for the validation of {@link RealtimeProperties}, so it can read the session
 * timeout from the environment.
 */
class EmitterTimeoutBelowSessionTimeoutValidator
        implements ConstraintValidator<EmitterTimeoutBelowSessionTimeout, RealtimeProperties> {

    private final Duration sessionTimeout;

    EmitterTimeoutBelowSessionTimeoutValidator(@Value("${server.servlet.session.timeout}") Duration sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    @Override
    public boolean isValid(RealtimeProperties properties, ConstraintValidatorContext context) {
        return properties.emitterTimeout().compareTo(sessionTimeout) < 0;
    }
}
