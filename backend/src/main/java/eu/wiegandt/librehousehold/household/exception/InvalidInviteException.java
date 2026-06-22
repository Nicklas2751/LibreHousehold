package eu.wiegandt.librehousehold.household.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class InvalidInviteException extends ErrorResponseException {

    public InvalidInviteException() {
        super(HttpStatus.NOT_FOUND);
    }
}
