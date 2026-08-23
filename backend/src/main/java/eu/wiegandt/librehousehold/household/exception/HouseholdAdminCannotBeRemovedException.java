package eu.wiegandt.librehousehold.household.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class HouseholdAdminCannotBeRemovedException extends ErrorResponseException {

    public HouseholdAdminCannotBeRemovedException() {
        super(HttpStatus.CONFLICT);
    }
}
