package eu.wiegandt.librehousehold.expenses.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ExpenseNotFoundException extends ErrorResponseException {

    public ExpenseNotFoundException() {
        super(HttpStatus.NOT_FOUND);
    }
}
