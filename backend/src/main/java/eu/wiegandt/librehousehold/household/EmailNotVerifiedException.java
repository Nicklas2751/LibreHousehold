package eu.wiegandt.librehousehold.household;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.net.URI;

/**
 * Part of the module's public API, because {@code config} answers a login of an unverified account
 * with it (see {@code ProblemDetailLoginFailureHandler}).
 */
public class EmailNotVerifiedException extends ErrorResponseException {

    public EmailNotVerifiedException() {
        super(HttpStatus.FORBIDDEN);
        this.setType(URI.create("/problems/email-not-verified"));
        this.setDetail("This action requires a verified email address.");
    }
}
