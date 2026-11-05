package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.household.AccountPrincipal;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Supplier;

/**
 * Grants access to protected endpoints only to sessions signed in as a member. Any other
 * authentication is denied, e.g. an external provider's authentication that survived the social
 * login callback, so {@code HouseholdAccessGuard} and {@code GET /me} can rely on the principal type.
 */
class AccountPrincipalAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    @Override
    public AuthorizationResult authorize(Supplier<? extends @Nullable Authentication> authentication,
                                         RequestAuthorizationContext context) {
        var current = authentication.get();
        return new AuthorizationDecision(current != null && current.getPrincipal() instanceof AccountPrincipal);
    }
}
