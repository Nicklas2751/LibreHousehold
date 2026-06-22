package eu.wiegandt.librehousehold.expenses.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class CategoryBodyIsRequiredException extends ErrorResponseException {

    public CategoryBodyIsRequiredException() {
        super(HttpStatus.BAD_REQUEST);
    }
}
