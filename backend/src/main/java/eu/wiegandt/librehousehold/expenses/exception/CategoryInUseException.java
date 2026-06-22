package eu.wiegandt.librehousehold.expenses.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class CategoryInUseException extends ErrorResponseException {

    public CategoryInUseException() {
        super(HttpStatus.CONFLICT);
    }
}
