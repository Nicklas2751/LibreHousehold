package eu.wiegandt.librehousehold.household.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class MemberNotFoundException extends ErrorResponseException {

    public MemberNotFoundException() {
        super(HttpStatus.NOT_FOUND);
    }
}
