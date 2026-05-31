package eu.wiegandt.librehousehold.household;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

class MemberNotFoundException extends ErrorResponseException {

    MemberNotFoundException() {
        super(HttpStatus.NOT_FOUND);
    }
}
