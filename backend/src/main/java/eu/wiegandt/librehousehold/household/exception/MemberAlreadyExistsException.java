package eu.wiegandt.librehousehold.household.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.net.URI;

public class MemberAlreadyExistsException extends ErrorResponseException {

    public MemberAlreadyExistsException() {
        super(HttpStatus.CONFLICT);
        this.setType(URI.create("/problems/account-already-exists"));
        this.setTitle("Account already exists");
        this.setDetail("An account for this email address already exists.");
    }
}
