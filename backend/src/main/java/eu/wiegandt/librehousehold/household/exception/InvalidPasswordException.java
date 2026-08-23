package eu.wiegandt.librehousehold.household.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class InvalidPasswordException extends ErrorResponseException {

    public InvalidPasswordException() {
        super(HttpStatus.FORBIDDEN);
    }
}
