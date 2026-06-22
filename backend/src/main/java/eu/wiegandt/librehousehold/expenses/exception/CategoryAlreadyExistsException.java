package eu.wiegandt.librehousehold.expenses.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class CategoryAlreadyExistsException extends ErrorResponseException {

    public CategoryAlreadyExistsException() {
        super(HttpStatus.CONFLICT);
    }
}
