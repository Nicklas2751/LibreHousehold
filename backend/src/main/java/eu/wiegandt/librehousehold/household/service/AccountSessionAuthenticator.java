package eu.wiegandt.librehousehold.household.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Programmatically establishes an authenticated Authorization Server session right after a new
 * account is created (household setup / invite join, see P1.4-follow-up). This lets the SPA
 * immediately continue with the existing {@code /oauth2/authorization/spa-backend-client} redirect
 * instead of hitting the login form.
 *
 * <p>Deliberately does <strong>not</strong> authenticate via the shared {@code AuthenticationManager}
 * (unlike a real {@code formLogin()}): that manager's {@code DaoAuthenticationProvider} runs
 * {@code UserDetails.isEnabled()} pre-authentication checks (see {@code AccountPrincipal#isEnabled()},
 * P2.2), which would reject this very first session too, since a freshly created account is always
 * unverified at this point — the login-blocking gate is meant for <em>subsequent</em> logins only
 * (see {@code docs/auth-plan-p2.1-p2.7.md}, Abschnitt 2.9). This class re-verifies the password
 * directly instead (redundant given the caller just used the same raw password to create the
 * account, but kept as a defense-in-depth safety net) and builds the authenticated token itself,
 * bypassing those pre-authentication checks entirely.
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
 * <p>{@code accountUserDetailsService} is injected {@code @Lazy}: it itself depends on
 * {@code MemberManagementService}, which depends on this class — an eager injection here would
 * create a circular reference back to the {@code MemberManagementService} bean still under
 * construction. Deferring resolution until the first actual authentication call breaks that cycle.
 */
@Component
public class AccountSessionAuthenticator {

    private final AccountUserDetailsService accountUserDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AccountSessionAuthenticator(@Lazy AccountUserDetailsService accountUserDetailsService,
                                        PasswordEncoder passwordEncoder) {
        this.accountUserDetailsService = accountUserDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    public void authenticateAndPersistSession(String email, String rawPassword) {
        var userDetails = accountUserDetailsService.loadUserByUsername(email);
        if (!passwordEncoder.matches(rawPassword, userDetails.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        var authenticationResult = UsernamePasswordAuthenticationToken.authenticated(
                userDetails, null, userDetails.getAuthorities());
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
