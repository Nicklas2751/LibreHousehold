package eu.wiegandt.librehousehold.config;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Cross-cutting concern for the whole API surface (not module-specific), hence placed in
 * {@code config} — see the "Module Dependency Direction" rule in Arc42 Chapter 5.
 *
 * <p>Every domain exception in this project (e.g. {@code HouseholdNotFoundException},
 * {@code MemberAlreadyExistsException}, {@code NoAuthenticatedSessionException}) already extends
 * {@link ErrorResponseException} and populates its {@link ProblemDetail} body via its own
 * constructor. Without this handler, none of them had a registered {@code @ExceptionHandler}, so
 * Spring MVC's {@code DefaultHandlerExceptionResolver} only ever resolved them via
 * {@code HttpServletResponse.sendError(...)} — which, behind a real servlet container, is
 * internally forwarded to Spring Boot's generic {@code BasicErrorController} instead of writing
 * the already-built {@link ProblemDetail} to the response (see {@code ValidationExceptionHandler}
 * for the same failure mode with Bean-Validation exceptions). This handler is intentionally
 * generic across all {@link ErrorResponseException} subtypes: none of them currently have their
 * own {@code @ExceptionHandler}, so there is no existing special-cased behavior to preserve.
 */
@RestControllerAdvice
public class ErrorResponseExceptionHandler {

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ProblemDetail> handleErrorResponseException(ErrorResponseException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .headers(exception.getHeaders())
                .body(exception.getBody());
    }
}
