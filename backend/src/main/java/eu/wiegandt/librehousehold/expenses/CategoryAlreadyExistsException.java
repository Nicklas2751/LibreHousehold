package eu.wiegandt.librehousehold.expenses;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

class CategoryAlreadyExistsException extends ErrorResponseException {

    CategoryAlreadyExistsException() {
        super(HttpStatus.CONFLICT);
    }
}
