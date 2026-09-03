package eu.wiegandt.librehousehold.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

/**
 * A {@link HttpSessionRequestCache} that never saves a request but still reads/resumes one saved
 * elsewhere. Used for {@code SecurityConfig#defaultSecurityFilterChain}, which shares its {@code
 * HttpSession} with {@code SecurityConfig#authorizationServerSecurityFilterChain}: an
 * unauthenticated 401 on a business endpoint (e.g. the frontend's own {@code bootstrapSession()},
 * which polls {basePath}/me on every page load, including the login page itself, before the login
 * form is submitted) must never overwrite the {@code /oauth2/authorize} continuation the other
 * chain already saved for the real login flow — while the {@code formLogin()} success handler on
 * this very chain still needs to read and resume exactly that saved request once the user
 * authenticates.
 */
class NonSavingRequestCache extends HttpSessionRequestCache {

    @Override
    public void saveRequest(HttpServletRequest request, HttpServletResponse response) {
        // Intentionally a no-op — see class Javadoc.
    }
}
