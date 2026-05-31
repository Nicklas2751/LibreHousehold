package eu.wiegandt.librehousehold.household;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

class MemberAlreadyExistsException extends ErrorResponseException {

    MemberAlreadyExistsException() {
        super(HttpStatus.CONFLICT);
    }
}
