package eu.wiegandt.librehousehold.household;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class HouseholdNotFoundException extends ErrorResponseException {

    HouseholdNotFoundException() {
        super(HttpStatus.NOT_FOUND);
    }
}
