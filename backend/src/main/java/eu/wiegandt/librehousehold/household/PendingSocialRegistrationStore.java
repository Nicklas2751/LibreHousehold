package eu.wiegandt.librehousehold.household;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Keeps the external identity of a social login with the SETUP or INVITE context in the
 * {@code HttpSession} until household setup or invite join turns it into a member. The external
 * authentication itself must not survive the provider callback (the embedded Authorization Server
 * would otherwise issue tokens for a subject that is no member), so the identity has to be kept
 * separately.
 *
 * <p>Part of the module's public API, because the social-login callback in {@code config} stores
 * the identity, while the {@code household} services read and consume it. The current request is
 * resolved via {@link RequestContextHolder} for the reason given in {@code AccountSessionAuthenticator}.
 */
@Component
public class PendingSocialRegistrationStore {

    private static final String SESSION_ATTRIBUTE = PendingSocialRegistrationStore.class.getName() + ".IDENTITY";

    public void store(ExternalIdentity identity) {
        currentRequest().getSession().setAttribute(SESSION_ATTRIBUTE, identity);
    }

    public Optional<ExternalIdentity> find() {
        var session = currentRequest().getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        return Optional.ofNullable((ExternalIdentity) session.getAttribute(SESSION_ATTRIBUTE));
    }

    /**
     * Removes the pending registration once it became a member, so it can be used only once.
     */
    public void consume() {
        var session = currentRequest().getSession(false);
        if (session != null) {
            session.removeAttribute(SESSION_ATTRIBUTE);
        }
    }

    private static HttpServletRequest currentRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }
}
