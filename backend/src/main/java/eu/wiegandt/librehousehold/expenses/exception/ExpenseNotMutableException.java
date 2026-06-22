package eu.wiegandt.librehousehold.expenses.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ExpenseNotMutableException extends ErrorResponseException {

    public ExpenseNotMutableException() {
        super(HttpStatus.CONFLICT);
    }
}
