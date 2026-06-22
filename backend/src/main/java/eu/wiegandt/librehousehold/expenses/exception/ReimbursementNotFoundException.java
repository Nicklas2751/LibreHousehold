package eu.wiegandt.librehousehold.expenses.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ReimbursementNotFoundException extends ErrorResponseException {

    public ReimbursementNotFoundException() {
        super(HttpStatus.NOT_FOUND);
    }
}
