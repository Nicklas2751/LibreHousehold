package eu.wiegandt.librehousehold.household.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class HouseholdSetupIsRequiredException extends ErrorResponseException {
    public HouseholdSetupIsRequiredException() {
        super(HttpStatus.BAD_REQUEST);
        this.setDetail("The body of setup must be set!");
    }
}
