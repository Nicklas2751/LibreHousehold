package eu.wiegandt.librehousehold.expenses.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ExpenseBodyIsRequiredException extends ErrorResponseException {

    public ExpenseBodyIsRequiredException() {
        super(HttpStatus.BAD_REQUEST);
    }
}
