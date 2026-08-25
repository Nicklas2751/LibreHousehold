package eu.wiegandt.librehousehold.config;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Cross-cutting concern for the whole API surface (not module-specific), hence placed in
 * {@code config} alongside {@code SecurityConfig}/{@code CsrfCookieFilter} rather than in any
 * business module — see the "Module Dependency Direction" rule in Arc42 Chapter 5.
 *
 * <p>{@link ConstraintViolationException} is thrown by AOP-based method validation
 * ({@code @Validated} on the generated {@code *Api} interfaces plus a constraint directly on a
 * {@code @RequestParam}/{@code @PathVariable}, e.g. {@code MembersApi.checkEmailAvailability}).
 * Unlike {@code @RequestBody @Valid} violations ({@link org.springframework.web.bind.MethodArgumentNotValidException}),
 * Spring MVC has no built-in translation of this exception into a {@code 400} response, so without
 * this handler it falls through to the generic error page, leaking a full stacktrace to the client.
 */
@RestControllerAdvice
public class ValidationExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
}
