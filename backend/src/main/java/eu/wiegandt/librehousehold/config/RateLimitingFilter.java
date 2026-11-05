package eu.wiegandt.librehousehold.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Enforces every configured {@link RateLimitRule} (RATE1 in Arc42 Chapter 8), rejecting a request
 * with a {@link TooManyRequestsProblem} once any matching rule's {@link RateLimiter} refuses it.
 * The problem is written through the MVC {@link HandlerExceptionResolver}, so it gets the same
 * RFC 9457 body as every other API error (see {@link ErrorResponseExceptionHandler}), although the
 * request never reaches a controller. No distinction between browser navigation and API calls is
 * needed here (unlike {@code ConcurrentSessionExpiredStrategy}): 429 is a meaningful status for both.
 */
class RateLimitingFilter extends OncePerRequestFilter {

    private final List<RateLimitRule> rules;
    private final HandlerExceptionResolver handlerExceptionResolver;

    RateLimitingFilter(List<RateLimitRule> rules, HandlerExceptionResolver handlerExceptionResolver) {
        this.rules = rules;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        var retryAfter = rules.stream()
                .filter(rule -> rule.appliesTo(request))
                .map(rule -> rule.acquire(request))
                .flatMap(Optional::stream)
                .findFirst();
        if (retryAfter.isPresent()) {
            handlerExceptionResolver.resolveException(request, response, null, new TooManyRequestsProblem(retryAfter.get()));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
