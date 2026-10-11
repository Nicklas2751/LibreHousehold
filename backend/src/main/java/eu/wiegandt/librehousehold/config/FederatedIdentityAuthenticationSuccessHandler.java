package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.model.SocialLoginContext.ContextEnum;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Maps a successful login at an external social-login provider to the member account linked to
 * that provider's subject (see ADR-018: only explicitly linked identities count, never a matching
 * e-mail address). Account lockout deliberately does not apply here: it defends against password
 * guessing, and no password is involved.
 *
 * <p>On success, the external authentication is replaced by one named after the member's e-mail
 * address, because the embedded Authorization Server issues the session's name as the ID token's
 * {@code sub}, which {@code AccountOidcUserService} resolves as e-mail. It carries an
 * {@link FactorGrantedAuthority#AUTHORIZATION_CODE_AUTHORITY} authority since the Authorization
 * Server's {@code JwtGenerator} needs a {@link FactorGrantedAuthority} to compute {@code auth_time}.
 * The {@link SecurityContextRepository} is instantiated locally for the reason given in
 * {@code AccountSessionAuthenticator}.
 *
 * <p>Without a linked account, the external authentication must not survive: the session is
 * invalidated and the user is sent back to the login page with a dedicated reason.
 */
@Component
public class FederatedIdentityAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    // A fresh SPA authorization instead of resuming the saved /oauth2/authorize request: the
    // redirect to the external provider replaced the SPA's pending authorization request in the
    // session (only one is kept), so resuming would fail with a state mismatch.
    private static final String SPA_CONTINUATION_PATH = "/oauth2/authorization/" + RegisteredClientSeeder.CLIENT_ID;
    private static final String NO_ACCOUNT_FAILURE_URL = "/login?error&reason=social-login-no-account";

    private final MemberQuery memberQuery;
    private final SocialLoginContextStore socialLoginContextStore;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final LogoutHandler logoutHandler = new SecurityContextLogoutHandler();
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    public FederatedIdentityAuthenticationSuccessHandler(MemberQuery memberQuery,
                                                         SocialLoginContextStore socialLoginContextStore) {
        this.memberQuery = memberQuery;
        this.socialLoginContextStore = socialLoginContextStore;
    }

    @Override
    public void onAuthenticationSuccess(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                        @NonNull Authentication authentication) throws IOException {
        var memberEmail = findLinkedMemberEmail((OAuth2AuthenticationToken) authentication);
        if (memberEmail.isEmpty()) {
            logoutHandler.logout(request, response, authentication);
            redirectStrategy.sendRedirect(request, response, NO_ACCOUNT_FAILURE_URL);
            return;
        }
        authenticateMember(memberEmail.get(), request, response);
        redirectStrategy.sendRedirect(request, response, SPA_CONTINUATION_PATH);
    }

    // Registering via social login (household setup, invite join) is not supported yet, so only a
    // plain login can succeed. The context is consumed in any case, so it never outlives this callback.
    private Optional<String> findLinkedMemberEmail(OAuth2AuthenticationToken authentication) {
        if (socialLoginContextStore.consume().getContext() != ContextEnum.LOGIN) {
            return Optional.empty();
        }
        return memberQuery.findEmailByExternalIdentity(authentication.getAuthorizedClientRegistrationId(),
                authentication.getName());
    }

    private void authenticateMember(String memberEmail, HttpServletRequest request, HttpServletResponse response) {
        var memberAuthentication = new PreAuthenticatedAuthenticationToken(memberEmail, null,
                List.of(FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.AUTHORIZATION_CODE_AUTHORITY)));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(memberAuthentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
