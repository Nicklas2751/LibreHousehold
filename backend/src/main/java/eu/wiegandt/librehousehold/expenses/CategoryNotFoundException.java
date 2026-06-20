package eu.wiegandt.librehousehold.expenses;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

class CategoryNotFoundException extends ErrorResponseException {

    CategoryNotFoundException() {
        super(HttpStatus.NOT_FOUND);
    }
}
