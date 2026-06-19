package eu.wiegandt.librehousehold.expenses;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

class ExpenseBodyIsRequiredException extends ErrorResponseException {

    ExpenseBodyIsRequiredException() {
        super(HttpStatus.BAD_REQUEST);
    }
}
