package eu.wiegandt.librehousehold.household.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.net.URI;

public class EmailNotVerifiedException extends ErrorResponseException {

    public EmailNotVerifiedException() {
        super(HttpStatus.FORBIDDEN);
        this.setType(URI.create("/problems/email-not-verified"));
        this.setDetail("This action requires a verified email address.");
    }
}
