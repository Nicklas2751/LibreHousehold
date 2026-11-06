package eu.wiegandt.librehousehold.config;

import eu.wiegandt.librehousehold.household.AccountSignIn;
import eu.wiegandt.librehousehold.household.ExternalIdentity;
import eu.wiegandt.librehousehold.household.PendingSocialRegistrationStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * Maps a successful login at an external social-login provider to the member account linked to
 * that provider's subject (see ADR-018: only explicitly linked identities count, never a matching
 * e-mail address). The provider is identified by {@link FederatedProviderKey}, not by the
 * registration ID. Account lockout deliberately does not apply here: it defends against password
 * guessing, and no password is involved.
 *
 * <p>The provider's tokens are not needed after the callback, so the authorized client is removed
 * in every case. With the LOGIN context, {@link AccountSignIn} replaces the external authentication
 * by the member's session; without a linked account, the session is invalidated and the user is
 * sent back to the login page with a dedicated reason.
 *
 * <p>With the SETUP or INVITE context, no account is created here. The external identity is kept
 * as a pending registration in the session, and household setup or invite join turn it into a
 * member after the user confirmed it. Only an e-mail address the provider verified
 * ({@code email_verified}, for GitHub see {@code GitHubEmailAwareOAuth2UserService}) may become a
 * member's e-mail, because it identifies the account (see ADR-018). The external authentication is
 * removed from the session in any case, since it does not identify a member.
 *
 * <p>Any failure invalidates the session, so a half-processed external authentication never
 * survives. The {@link SecurityContextRepository} is instantiated locally for the reason given in
 * {@code AccountSessionSignIn}. The {@link ClientRegistrationRepository} only exists if social login
 * is configured, which is also the only case in which this handler is called.
 */
@Component
public class FederatedIdentityAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final String DASHBOARD_PAGE = "/app/dashboard";
    private static final String NO_ACCOUNT_FAILURE_URL = "/login?error&reason=social-login-no-account";
    static final String SOCIAL_LOGIN_FAILURE_URL = "/login?error&reason=social-login-failed";
    private static final String SETUP_PAGE = "/setup";
    private static final String INVITE_PAGE_PREFIX = "/invite/";
    private static final String SOCIAL_REGISTRATION_QUERY = "?social";
    private static final String EMAIL_UNVERIFIED_QUERY = "?error&reason=social-email-unverified";

    private final AccountSignIn accountSignIn;
    private final SocialLoginContextStore socialLoginContextStore;
    private final PendingSocialRegistrationStore pendingSocialRegistrationStore;
    private final OAuth2AuthorizedClientRepository authorizedClientRepository;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final LogoutHandler logoutHandler = new SecurityContextLogoutHandler();
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    public FederatedIdentityAuthenticationSuccessHandler(AccountSignIn accountSignIn,
                                                         SocialLoginContextStore socialLoginContextStore,
                                                         PendingSocialRegistrationStore pendingSocialRegistrationStore,
                                                         OAuth2AuthorizedClientRepository authorizedClientRepository,
                                                         ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository) {
        this.accountSignIn = accountSignIn;
        this.socialLoginContextStore = socialLoginContextStore;
        this.pendingSocialRegistrationStore = pendingSocialRegistrationStore;
        this.authorizedClientRepository = authorizedClientRepository;
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Override
    public void onAuthenticationSuccess(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                        @NonNull Authentication authentication) throws IOException {
        // oauth2Login() always authenticates with an OAuth2AuthenticationToken
        var externalAuthentication = (OAuth2AuthenticationToken) authentication;
        try {
            authorizedClientRepository.removeAuthorizedClient(
                    externalAuthentication.getAuthorizedClientRegistrationId(), externalAuthentication, request, response);
            handle(externalAuthentication, request, response);
        } catch (RuntimeException _) {
            logoutHandler.logout(request, response, externalAuthentication);
            redirectStrategy.sendRedirect(request, response, SOCIAL_LOGIN_FAILURE_URL);
        }
    }

    private void handle(OAuth2AuthenticationToken authentication, HttpServletRequest request,
                        HttpServletResponse response) throws IOException {
        var context = socialLoginContextStore.consume();
        switch (context.getContext()) {
            case LOGIN -> login(authentication, request, response);
            case SETUP -> startRegistration(SETUP_PAGE, authentication, request, response);
            case INVITE -> startRegistration(INVITE_PAGE_PREFIX + context.getInviteToken().orElseThrow(),
                    authentication, request, response);
        }
    }

    private void login(OAuth2AuthenticationToken authentication, HttpServletRequest request,
                       HttpServletResponse response) throws IOException {
        var memberId = accountSignIn.signInByExternalIdentity(providerId(authentication), authentication.getName());
        if (memberId.isEmpty()) {
            logoutHandler.logout(request, response, authentication);
            redirectStrategy.sendRedirect(request, response, NO_ACCOUNT_FAILURE_URL);
            return;
        }
        redirectStrategy.sendRedirect(request, response, DASHBOARD_PAGE);
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
        pendingSocialRegistrationStore.store(new ExternalIdentity(providerId(authentication), authentication.getName(),
                verifiedEmail.get(), externalUser.getAttribute(StandardClaimNames.NAME)));
        discardExternalAuthentication(request, response);
        redirectStrategy.sendRedirect(request, response, registrationPage + SOCIAL_REGISTRATION_QUERY);
    }

    private String providerId(OAuth2AuthenticationToken authentication) {
        var registration = clientRegistrationRepository.getObject()
                .findByRegistrationId(authentication.getAuthorizedClientRegistrationId());
        return FederatedProviderKey.of(registration);
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
}
