package eu.wiegandt.librehousehold.household.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class NoPendingSocialRegistrationException extends ErrorResponseException {

    public NoPendingSocialRegistrationException() {
        super(HttpStatus.BAD_REQUEST);
    }
}
