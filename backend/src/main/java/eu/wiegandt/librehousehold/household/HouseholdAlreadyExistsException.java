package eu.wiegandt.librehousehold.household;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

class HouseholdAlreadyExistsException extends ErrorResponseException {
    HouseholdAlreadyExistsException() {
        super(HttpStatus.CONFLICT);
        this.setDetail("A household has already been set up.");
    }
}