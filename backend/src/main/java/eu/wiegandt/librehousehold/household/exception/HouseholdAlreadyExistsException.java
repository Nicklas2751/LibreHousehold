package eu.wiegandt.librehousehold.household.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class HouseholdAlreadyExistsException extends ErrorResponseException {
    public HouseholdAlreadyExistsException() {
        super(HttpStatus.CONFLICT);
        this.setDetail("A household has already been set up.");
    }
}