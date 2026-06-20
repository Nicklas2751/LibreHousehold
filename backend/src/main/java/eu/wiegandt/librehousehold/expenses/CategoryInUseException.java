package eu.wiegandt.librehousehold.expenses;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

class CategoryInUseException extends ErrorResponseException {

    CategoryInUseException() {
        super(HttpStatus.CONFLICT);
    }
}
