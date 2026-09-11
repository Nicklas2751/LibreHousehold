package eu.wiegandt.librehousehold.household.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.net.URI;

public class AccountTokenInvalidException extends ErrorResponseException {

    public AccountTokenInvalidException() {
        super(HttpStatus.CONFLICT);
        this.setType(URI.create("/problems/verification-token-invalid"));
        this.setDetail("This verification link is invalid or has expired.");
    }
}
