package eu.wiegandt.librehousehold.household.exception;
import eu.wiegandt.librehousehold.household.exception.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class HouseholdAlreadyExistsExceptionTest {

    @Test
    void constructor_noArgs_hasConflictStatus() {
        // when
        var exception = new HouseholdAlreadyExistsException();

        // then
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void constructor_noArgs_hasDetailMessage() {
        // when
        var exception = new HouseholdAlreadyExistsException();

        // then
        assertThat(exception.getBody().getDetail()).isEqualTo("A household has already been set up.");
    }
}