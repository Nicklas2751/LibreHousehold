package eu.wiegandt.librehousehold.household;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

class InvalidInviteException extends ErrorResponseException {

    InvalidInviteException() {
        super(HttpStatus.NOT_FOUND);
    }
}
