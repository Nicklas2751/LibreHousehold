package eu.wiegandt.librehousehold.household.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.net.URI;

public class PasswordResetTokenInvalidException extends ErrorResponseException {

    public PasswordResetTokenInvalidException() {
        super(HttpStatus.CONFLICT);
        this.setType(URI.create("/problems/password-reset-token-invalid"));
        this.setDetail("This password reset link is invalid or has expired.");
    }
}
