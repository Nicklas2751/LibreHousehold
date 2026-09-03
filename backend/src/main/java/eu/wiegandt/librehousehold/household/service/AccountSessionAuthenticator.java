package eu.wiegandt.librehousehold.household.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Programmatically establishes an authenticated Authorization Server session right after a new
 * account is created (household setup / invite join, see P1.4-follow-up), functionally identical
 * to a successful {@code formLogin()} against {@code AccountUserDetailsService}. This lets the SPA
 * immediately continue with the existing {@code /oauth2/authorization/spa-backend-client} redirect
 * instead of hitting the login form.
 *
 * <p>The current request/response are resolved via {@link RequestContextHolder} inside
 * {@link #authenticateAndPersistSession} rather than constructor-injected as request-scoped beans:
 * {@code HouseholdSetupService}/{@code MemberManagementService} (this class's only callers) are
 * core {@code @Service} beans that also get constructed in full-context tests using a non-web
 * {@code ApplicationContext} (e.g. {@code @SpringBootTest(webEnvironment = WebEnvironment.NONE)}).
 * Such a context never registers {@code HttpServletRequest}/{@code HttpServletResponse} as
 * resolvable dependencies at all, so constructor injection would fail to even build the
 * application context there, regardless of whether the flow is ever invoked. Resolving them lazily
 * here only matters at actual call time, which — in production — is always from within a live HTTP
 * request.
 *
 * <p>{@link SecurityContextRepository} is instantiated locally rather than injected as a shared
 * bean: exposing a {@code SecurityContextRepository} bean risks being auto-detected by
 * {@code HttpSecurity}'s own {@code securityContext()} configurer for the two existing filter
 * chains, silently changing their default context-persistence behavior — this class only ever
 * needs the plain, stateless {@link HttpSessionSecurityContextRepository}.
 *
 * <p>{@code authenticationManager} is injected {@code @Lazy}: building the real
 * {@link AuthenticationManager} eagerly resolves {@code UserDetailsService} (i.e.
 * {@code AccountUserDetailsService}, which itself depends on {@code MemberManagementService})
 * during bean creation. Since {@code MemberManagementService} depends on this class, an eager
 * {@link AuthenticationManager} injection here creates a circular reference back to the
 * {@code MemberManagementService} bean still under construction. Deferring resolution until the
 * first actual authentication call breaks that cycle.
 */
@Component
public class AccountSessionAuthenticator {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AccountSessionAuthenticator(@Lazy AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    public void authenticateAndPersistSession(String email, String rawPassword) {
        var authenticationRequest = UsernamePasswordAuthenticationToken.unauthenticated(email, rawPassword);
        var authenticationResult = authenticationManager.authenticate(authenticationRequest);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticationResult);
        SecurityContextHolder.setContext(context);
        var requestAttributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        var response = requestAttributes.getResponse();
        if (response == null) {
            throw new IllegalStateException("No HttpServletResponse available for the current request");
        }
        securityContextRepository.saveContext(context, requestAttributes.getRequest(), response);
    }
}
