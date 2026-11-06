package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.household.AccountLockedException;
import eu.wiegandt.librehousehold.household.EmailNotVerifiedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Answers a failed form login with an RFC 9457 problem, written through the MVC
 * {@link HandlerExceptionResolver} like every other API error (see {@link RateLimitingFilter}).
 * Every failure that could reveal an existing account gets the same invalid-credentials problem
 * (ENUM1 in Arc42 Chapter 8). Only an unverified account with the correct password (see
 * {@link AuthenticationProviderConfig}) and a locked account are told apart, so the SPA can show a
 * specific hint. The exception's message never reaches the response.
 *
 * <p>{@code AccountLockedException} is thrown from {@code AccountUserDetailsService}, which
 * {@code DaoAuthenticationProvider} wraps into an {@code InternalAuthenticationServiceException},
 * so it usually arrives as the cause. Any other {@link AuthenticationServiceException} is a server
 * error, not a login failure. The resolver is {@link Lazy} for the reason given in
 * {@code RateLimitingConfig}.
 */
@Component
class ProblemDetailLoginFailureHandler implements AuthenticationFailureHandler {

    private final HandlerExceptionResolver handlerExceptionResolver;

    ProblemDetailLoginFailureHandler(@Lazy @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    public void onAuthenticationFailure(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                        @NonNull AuthenticationException exception) {
        handlerExceptionResolver.resolveException(request, response, null, toProblem(exception));
    }

    private static ErrorResponseException toProblem(AuthenticationException exception) {
        var lockedException = findAccountLockedException(exception);
        if (lockedException != null) {
            return new AccountLockedProblem(lockedException.getLockedUntil());
        }
        return switch (exception) {
            case DisabledException _ -> new EmailNotVerifiedException();
            case AuthenticationServiceException _ -> new ErrorResponseException(HttpStatus.INTERNAL_SERVER_ERROR);
            default -> new InvalidCredentialsProblem();
        };
    }

    private static @Nullable AccountLockedException findAccountLockedException(AuthenticationException exception) {
        if (exception instanceof AccountLockedException direct) {
            return direct;
        }
        return exception.getCause() instanceof AccountLockedException cause ? cause : null;
    }
}
