package eu.wiegandt.librehousehold.tasks.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class HouseholdNotFoundException extends ErrorResponseException {

    public HouseholdNotFoundException() {
        super(HttpStatus.NOT_FOUND);
    }
}
