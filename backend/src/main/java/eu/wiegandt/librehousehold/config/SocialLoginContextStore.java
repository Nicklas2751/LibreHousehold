package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.model.SocialLoginContext;
import eu.wiegandt.librehousehold.model.SocialLoginContext.ContextEnum;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Remembers in the {@code HttpSession} why the SPA starts a social-login redirect (login, household
 * setup or invite join), so the callback from the external provider can act on it: the provider
 * round trip itself cannot carry this information.
 *
 * <p>The current request is resolved via {@link RequestContextHolder} instead of being passed in:
 * the generated {@code SessionApiDelegate} methods that store the context get no request parameter
 * (same reasoning as {@code AccountSessionAuthenticator}).
 */
@Component
public class SocialLoginContextStore {

    private static final String SESSION_ATTRIBUTE = SocialLoginContextStore.class.getName() + ".CONTEXT";

    public void store(SocialLoginContext context) {
        currentRequest().getSession().setAttribute(SESSION_ATTRIBUTE, context);
    }

    /**
     * Returns the stored context and removes it from the session, so it applies to exactly one
     * social-login callback. Without a stored context, the callback is a plain login.
     */
    public SocialLoginContext consume() {
        var session = currentRequest().getSession(false);
        if (session == null || !(session.getAttribute(SESSION_ATTRIBUTE) instanceof SocialLoginContext stored)) {
            return new SocialLoginContext(ContextEnum.LOGIN);
        }
        session.removeAttribute(SESSION_ATTRIBUTE);
        return stored;
    }

    private static HttpServletRequest currentRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }
}
