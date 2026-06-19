package eu.wiegandt.librehousehold.expenses;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

class ReimbursementNotFoundException extends ErrorResponseException {

    ReimbursementNotFoundException() {
        super(HttpStatus.NOT_FOUND);
    }
}
