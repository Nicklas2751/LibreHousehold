package eu.wiegandt.librehousehold.config;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.util.BindErrorUtils;

/**
 * Cross-cutting concern for the whole API surface (not module-specific), hence placed in
 * {@code config} alongside {@code SecurityConfig}/{@code CsrfCookieFilter} rather than in any
 * business module — see the "Module Dependency Direction" rule in Arc42 Chapter 5.
 *
 * <p>Both exceptions handled here are Bean-Validation failures that Spring MVC's own
 * {@code DefaultHandlerExceptionResolver} only turns into a {@code 400} status via
 * {@code HttpServletResponse.sendError(...)}, never into an actual response body: outside a
 * servlet container that resolution just leaves the body empty (see the WebMvcTest-based
 * {@code *ValidationIT} classes), but a real container internally forwards that {@code sendError}
 * call to Spring Boot's generic {@code BasicErrorController} (mapped to {@code /error}), which
 * renders the classic {@code timestamp/status/error/trace/message/path} envelope — including a
 * full stacktrace in the {@code trace} field regardless of {@code spring.web.error.include-stacktrace}
 * only being effective for that fallback page, not for this one. These handlers pre-empt that
 * fallback entirely with a clean {@link ProblemDetail} response.
 */
@RestControllerAdvice
public class ValidationExceptionHandler {

    /**
     * {@link ConstraintViolationException} is thrown by AOP-based method validation
     * ({@code @Validated} on the generated {@code *Api} interfaces plus a constraint directly on a
     * {@code @RequestParam}/{@code @PathVariable}, e.g. {@code MembersApi.checkEmailAvailability}).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /**
     * Thrown for {@code @RequestBody @Valid} violations. The field errors are joined into the
     * {@code detail} message (e.g. {@code "household: must not be null, and member: must not be
     * null"}) via {@link BindErrorUtils}, the same utility Spring's own
     * {@code ResponseEntityExceptionHandler} uses internally to resolve these messages.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        var detail = BindErrorUtils.resolveAndJoin(exception.getFieldErrors());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }
}
