package eu.wiegandt.librehousehold.tasks;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

class HouseholdNotFoundException extends ErrorResponseException {

    HouseholdNotFoundException() {
        super(HttpStatus.NOT_FOUND);
    }
}
