package eu.wiegandt.librehousehold.expenses.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class CategoryNotFoundException extends ErrorResponseException {

    public CategoryNotFoundException() {
        super(HttpStatus.NOT_FOUND);
    }
}
