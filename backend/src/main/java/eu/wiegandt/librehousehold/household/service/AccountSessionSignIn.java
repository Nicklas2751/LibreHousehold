package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.AccountPrincipal;
import eu.wiegandt.librehousehold.household.AccountSignIn;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

/**
 * Establishes a session without a password check, on exactly two occasions:
 * <ul>
 *     <li>{@link #signIn(UUID)}: right after household setup or invite join, called by the
 *     controllers once the registration transaction has committed. It deliberately bypasses
 *     {@link AccountPrincipal#isEnabled()}: a freshly registered account is always unverified,
 *     and the login block only applies to later logins. Module-internal on purpose; no other
 *     module may sign in a member by ID alone.</li>
 *     <li>{@link #signInByExternalIdentity}: in the social-login callback, after
 *     {@code oauth2Login()} has already renewed the session ID and registered the session.</li>
 * </ul>
 * Both paths renew the session ID exactly once and register the session in the
 * {@link SessionRegistry}, so a password reset ends it (RESET1 in Arc42 Chapter 8).
 *
 * <p>The current request and response are resolved via {@link RequestContextHolder} instead of
 * being passed in, because the generated API delegates that call this class get none. The
 * {@link SecurityContextRepository} is instantiated locally: a bean of that type would be picked up
 * by {@code HttpSecurity} and change how the filter chain persists the context.
 */
@Component
public class AccountSessionSignIn implements AccountSignIn {

    private final AccountUserDetailsService accountUserDetailsService;
    private final AccountService accountService;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SessionRegistry sessionRegistry;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AccountSessionSignIn(AccountUserDetailsService accountUserDetailsService, AccountService accountService,
                                SessionAuthenticationStrategy sessionAuthenticationStrategy,
                                SessionRegistry sessionRegistry) {
        this.accountUserDetailsService = accountUserDetailsService;
        this.accountService = accountService;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.sessionRegistry = sessionRegistry;
    }

    public void signIn(UUID memberId) {
        var authentication = authenticationFor(memberId);
        var request = currentRequest();
        var response = currentResponse();
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        saveContext(authentication, request, response);
    }

    @Override
    public Optional<UUID> signInByExternalIdentity(String providerId, String externalSubject) {
        var memberId = accountService.findMemberIdByExternalIdentity(providerId, externalSubject);
        memberId.ifPresent(this::replaceExternalAuthentication);
        return memberId;
    }

    // oauth2Login() already renewed the session ID; only the principal it registered is replaced
    private void replaceExternalAuthentication(UUID memberId) {
        var authentication = authenticationFor(memberId);
        var request = currentRequest();
        saveContext(authentication, request, currentResponse());
        sessionRegistry.registerNewSession(request.getSession().getId(), authentication.getPrincipal());
    }

    private Authentication authenticationFor(UUID memberId) {
        var principal = accountUserDetailsService.loadUserByMemberId(memberId);
        principal.eraseCredentials();
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    private void saveContext(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private static HttpServletRequest currentRequest() {
        return currentRequestAttributes().getRequest();
    }

    private static HttpServletResponse currentResponse() {
        var response = currentRequestAttributes().getResponse();
        if (response == null) {
            throw new IllegalStateException("No HttpServletResponse available for the current request");
        }
        return response;
    }

    private static ServletRequestAttributes currentRequestAttributes() {
        return (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
    }
}
