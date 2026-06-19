package eu.wiegandt.librehousehold.expenses;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

class ExpenseNotFoundException extends ErrorResponseException {

    ExpenseNotFoundException() {
        super(HttpStatus.NOT_FOUND);
    }
}
