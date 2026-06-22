package eu.wiegandt.librehousehold.household.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class MemberAlreadyExistsException extends ErrorResponseException {

    public MemberAlreadyExistsException() {
        super(HttpStatus.CONFLICT);
    }
}
