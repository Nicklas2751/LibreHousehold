package eu.wiegandt.librehousehold.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;

/**
 * Runs after {@code CsrfAuthenticationStrategy}, which removes the old token on sign-in and only
 * defers a new one. {@link CsrfCookieFilter} has already run for this request, so without this
 * strategy the response would just delete the XSRF-TOKEN cookie, and the SPA's next POST would fail
 * with 403. Reading the deferred token makes the cookie repository write the new one.
 */
class CsrfTokenMaterializingStrategy implements SessionAuthenticationStrategy {

    @Override
    public void onAuthentication(@NonNull Authentication authentication, HttpServletRequest request,
                                 @NonNull HttpServletResponse response) {
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken csrfToken) {
            csrfToken.getToken();
        }
    }
}
