package eu.wiegandt.librehousehold.realtime.config;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Fails the startup if {@code librehousehold.realtime.emitter-timeout} is not shorter than
 * {@code server.servlet.session.timeout}: a stream must never outlive its session (RT4), and each
 * reconnect after the emitter timeout runs the authentication and membership check again.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EmitterTimeoutBelowSessionTimeoutValidator.class)
@interface EmitterTimeoutBelowSessionTimeout {

    String message() default "emitter timeout must be shorter than the session timeout";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
