package eu.wiegandt.librehousehold.household.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.net.URI;

public class HouseholdAlreadyExistsException extends ErrorResponseException {
    public HouseholdAlreadyExistsException() {
        super(HttpStatus.CONFLICT);
        this.setType(URI.create("/problems/household-already-exists"));
        this.setTitle("Household already exists");
        this.setDetail("A household has already been set up.");
    }
}
