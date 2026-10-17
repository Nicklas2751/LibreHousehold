package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.household.ExternalIdentity;
import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.household.PendingSocialRegistrationStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
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
 *
 * <p>With the SETUP or INVITE context, no account is created here. The external identity is kept
 * as a pending registration in the session, and household setup or invite join turn it into a
 * member after the user confirmed it. Only an e-mail address the provider verified
 * ({@code email_verified}, for GitHub see {@code GitHubEmailAwareOAuth2UserService}) may become a
 * member's e-mail, because it identifies the account (see ADR-018). The external authentication is
 * removed from the security context and the session in any case: the embedded Authorization Server
 * would otherwise issue tokens for a subject that is no member.
 */
@Component
public class FederatedIdentityAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    // A fresh SPA authorization instead of resuming the saved /oauth2/authorize request: the
    // redirect to the external provider replaced the SPA's pending authorization request in the
    // session (only one is kept), so resuming would fail with a state mismatch.
    private static final String SPA_CONTINUATION_PATH = "/oauth2/authorization/" + RegisteredClientSeeder.CLIENT_ID;
    private static final String NO_ACCOUNT_FAILURE_URL = "/login?error&reason=social-login-no-account";
    private static final String SETUP_PAGE = "/setup";
    private static final String INVITE_PAGE_PREFIX = "/invite/";
    private static final String SOCIAL_REGISTRATION_QUERY = "?social";
    private static final String EMAIL_UNVERIFIED_QUERY = "?error&reason=social-email-unverified";

    private final MemberQuery memberQuery;
    private final SocialLoginContextStore socialLoginContextStore;
    private final PendingSocialRegistrationStore pendingSocialRegistrationStore;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final LogoutHandler logoutHandler = new SecurityContextLogoutHandler();
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
    // Same session attribute the authorization server chain saves the /oauth2/authorize request in.
    private final RequestCache requestCache = new HttpSessionRequestCache();

    public FederatedIdentityAuthenticationSuccessHandler(MemberQuery memberQuery,
                                                         SocialLoginContextStore socialLoginContextStore,
                                                         PendingSocialRegistrationStore pendingSocialRegistrationStore) {
        this.memberQuery = memberQuery;
        this.socialLoginContextStore = socialLoginContextStore;
        this.pendingSocialRegistrationStore = pendingSocialRegistrationStore;
    }

    @Override
    public void onAuthenticationSuccess(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                        @NonNull Authentication authentication) throws IOException {
        // oauth2Login() always authenticates with an OAuth2AuthenticationToken. The context is
        // consumed in any case, so it never outlives this callback.
        var externalAuthentication = (OAuth2AuthenticationToken) authentication;
        // Every path ends in a fresh SPA authorization, either right away or after setup/invite
        // join. An /oauth2/authorize request saved before the social login (the SPA's own login
        // ran into /login first) is stale: the SPA client's success handler would resume it after
        // the fresh authorization and fail, because its authorization request no longer exists.
        requestCache.removeRequest(request, response);
        var context = socialLoginContextStore.consume();
        switch (context.getContext()) {
            case LOGIN -> login(externalAuthentication, request, response);
            case SETUP -> startRegistration(SETUP_PAGE, externalAuthentication, request, response);
            case INVITE -> startRegistration(INVITE_PAGE_PREFIX + context.getInviteToken().orElseThrow(),
                    externalAuthentication, request, response);
        }
    }

    private void login(OAuth2AuthenticationToken authentication, HttpServletRequest request,
                       HttpServletResponse response) throws IOException {
        var memberEmail = memberQuery.findEmailByExternalIdentity(authentication.getAuthorizedClientRegistrationId(),
                authentication.getName());
        if (memberEmail.isEmpty()) {
            logoutHandler.logout(request, response, authentication);
            redirectStrategy.sendRedirect(request, response, NO_ACCOUNT_FAILURE_URL);
            return;
        }
        authenticateMember(memberEmail.get(), request, response);
        redirectStrategy.sendRedirect(request, response, SPA_CONTINUATION_PATH);
    }

    private void startRegistration(String registrationPage, OAuth2AuthenticationToken authentication,
                                   HttpServletRequest request, HttpServletResponse response) throws IOException {
        var externalUser = authentication.getPrincipal();
        var verifiedEmail = findVerifiedEmail(externalUser);
        if (verifiedEmail.isEmpty()) {
            logoutHandler.logout(request, response, authentication);
            redirectStrategy.sendRedirect(request, response, registrationPage + EMAIL_UNVERIFIED_QUERY);
            return;
        }
        pendingSocialRegistrationStore.store(new ExternalIdentity(authentication.getAuthorizedClientRegistrationId(),
                authentication.getName(), verifiedEmail.get(), externalUser.getAttribute(StandardClaimNames.NAME)));
        discardExternalAuthentication(request, response);
        redirectStrategy.sendRedirect(request, response, registrationPage + SOCIAL_REGISTRATION_QUERY);
    }

    private Optional<String> findVerifiedEmail(OAuth2User externalUser) {
        if (!Boolean.TRUE.equals(externalUser.getAttribute(StandardClaimNames.EMAIL_VERIFIED))) {
            return Optional.empty();
        }
        return Optional.ofNullable(externalUser.getAttribute(StandardClaimNames.EMAIL));
    }

    // Unlike a logout, the session survives: it carries the pending registration.
    private void discardExternalAuthentication(HttpServletRequest request, HttpServletResponse response) {
        var emptyContext = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(emptyContext);
        securityContextRepository.saveContext(emptyContext, request, response);
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
