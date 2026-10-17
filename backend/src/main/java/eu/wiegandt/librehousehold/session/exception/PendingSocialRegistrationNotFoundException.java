package eu.wiegandt.librehousehold.session.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class PendingSocialRegistrationNotFoundException extends ErrorResponseException {

    public PendingSocialRegistrationNotFoundException() {
        super(HttpStatus.NOT_FOUND);
    }
}
