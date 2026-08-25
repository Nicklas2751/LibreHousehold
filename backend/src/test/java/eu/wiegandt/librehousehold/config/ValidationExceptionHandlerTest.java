package eu.wiegandt.librehousehold.config;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationExceptionHandlerTest {

    private final ValidationExceptionHandler validationExceptionHandler = new ValidationExceptionHandler();

    @Test
    void handleConstraintViolation_constraintViolationException_badRequestProblemDetailWithoutStacktrace() {
        // given
        var exceptionMessage = "checkEmailAvailability.email: must be a well-formed email address";
        var constraintViolationException = new ConstraintViolationException(exceptionMessage, Set.of());
        var expected = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exceptionMessage);

        // when
        var result = validationExceptionHandler.handleConstraintViolation(constraintViolationException);

        // then
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }
}
