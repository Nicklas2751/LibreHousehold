package eu.wiegandt.librehousehold.expenses;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

class CategoryBodyIsRequiredException extends ErrorResponseException {

    CategoryBodyIsRequiredException() {
        super(HttpStatus.BAD_REQUEST);
    }
}
