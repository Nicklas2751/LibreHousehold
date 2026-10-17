package eu.wiegandt.librehousehold.session.controller;

import eu.wiegandt.librehousehold.api.SessionApiDelegate;
import eu.wiegandt.librehousehold.household.LocalLoginProperties;
import eu.wiegandt.librehousehold.config.SocialLoginContextStore;
import eu.wiegandt.librehousehold.config.SocialLoginProperties;
import eu.wiegandt.librehousehold.config.SocialLoginProperties.ProviderConfig;
import eu.wiegandt.librehousehold.household.AccountOidcPrincipal;
import eu.wiegandt.librehousehold.household.ExternalIdentity;
import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.household.PasswordReset;
import eu.wiegandt.librehousehold.household.PendingSocialRegistrationStore;
import eu.wiegandt.librehousehold.model.AuthDiscovery;
import eu.wiegandt.librehousehold.model.CurrentUser;
import eu.wiegandt.librehousehold.model.PasswordResetConfirm;
import eu.wiegandt.librehousehold.model.PasswordResetRequest;
import eu.wiegandt.librehousehold.model.PendingSocialRegistration;
import eu.wiegandt.librehousehold.model.SocialLoginContext;
import eu.wiegandt.librehousehold.model.SocialLoginContext.ContextEnum;
import eu.wiegandt.librehousehold.model.SocialLoginProvider;
import eu.wiegandt.librehousehold.session.exception.InviteTokenIsRequiredException;
import eu.wiegandt.librehousehold.session.exception.NoAuthenticatedSessionException;
import eu.wiegandt.librehousehold.session.exception.PendingSocialRegistrationNotFoundException;
import eu.wiegandt.librehousehold.usersettings.PreferencesQuery;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * Handles {@code GET /me} (see P1.4.7), the password-reset endpoints, storing the social-login
 * context and the login-method discovery. {@code POST /logout} is
 * deliberately not implemented here: it is fully handled by Spring Security's own {@code logout()}
 * filter (see {@code SecurityConfig}/{@code RevokeAuthorizedClientLogoutHandler}), which intercepts
 * the request before it would ever reach this controller.
 *
 * <p>The password-reset endpoints share the OpenAPI {@code session} tag (and therefore this single
 * generated delegate) even though their business logic belongs to {@code household}. Delegated to
 * via the {@link PasswordReset} Named Interface rather than household's internal service classes,
 * since this class lives in the {@code session} module (see ADR-011).
 *
 * <p>{@code GET /auth/social-registration} shows the client which external identity a pending
 * social-login registration will use, so the user can confirm it before setup or invite join.
 * Only name and e-mail leave the server, never the provider's subject.
 *
 * <p>{@code GET /auth/discover} tells the client before any login which login methods this instance
 * offers, read from {@link LocalLoginProperties} and {@link SocialLoginProperties}. Only the
 * registration ID and display name of each social provider leave the server, never client
 * credentials or endpoint configuration.
 */
@Component
public class SessionApiDelegateImpl implements SessionApiDelegate {

    private final MemberQuery memberQuery;
    private final HouseholdQuery householdQuery;
    private final PreferencesQuery preferencesQuery;
    private final PasswordReset passwordReset;
    private final SocialLoginContextStore socialLoginContextStore;
    private final SocialLoginProperties socialLoginProperties;
    private final LocalLoginProperties localLoginProperties;
    private final PendingSocialRegistrationStore pendingSocialRegistrationStore;

    public SessionApiDelegateImpl(MemberQuery memberQuery, HouseholdQuery householdQuery,
                                   PreferencesQuery preferencesQuery, PasswordReset passwordReset,
                                   SocialLoginContextStore socialLoginContextStore,
                                   SocialLoginProperties socialLoginProperties,
                                   LocalLoginProperties localLoginProperties,
                                   PendingSocialRegistrationStore pendingSocialRegistrationStore) {
        this.memberQuery = memberQuery;
        this.householdQuery = householdQuery;
        this.preferencesQuery = preferencesQuery;
        this.passwordReset = passwordReset;
        this.socialLoginContextStore = socialLoginContextStore;
        this.socialLoginProperties = socialLoginProperties;
        this.localLoginProperties = localLoginProperties;
        this.pendingSocialRegistrationStore = pendingSocialRegistrationStore;
    }

    @Override
    public ResponseEntity<CurrentUser> getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountOidcPrincipal principal)) {
            throw new NoAuthenticatedSessionException();
        }
        var member = memberQuery.getMember(principal.memberId());
        var household = householdQuery.getHousehold(principal.householdId());
        var preferences = preferencesQuery.getPreferencesOrDefault(principal.memberId());
        var emailVerified = memberQuery.isEmailVerified(principal.memberId());
        return ResponseEntity.ok(new CurrentUser(member, household, preferences, emailVerified));
    }

    @Override
    public ResponseEntity<Void> requestPasswordReset(PasswordResetRequest passwordResetRequest) {
        passwordReset.requestPasswordReset(passwordResetRequest.getEmail());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @Override
    public ResponseEntity<Void> confirmPasswordReset(PasswordResetConfirm passwordResetConfirm) {
        passwordReset.confirmPasswordReset(passwordResetConfirm.getToken(), passwordResetConfirm.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> storeSocialLoginContext(SocialLoginContext socialLoginContext) {
        if (socialLoginContext.getContext() == ContextEnum.INVITE && socialLoginContext.getInviteToken().isEmpty()) {
            throw new InviteTokenIsRequiredException();
        }
        socialLoginContextStore.store(socialLoginContext);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<AuthDiscovery> getAuthDiscovery() {
        return ResponseEntity.ok(new AuthDiscovery(localLoginProperties.enabled(), socialProviders()));
    }

    @Override
    public ResponseEntity<PendingSocialRegistration> getPendingSocialRegistration() {
        return pendingSocialRegistrationStore.find()
                .map(SessionApiDelegateImpl::toPendingSocialRegistration)
                .map(ResponseEntity::ok)
                .orElseThrow(PendingSocialRegistrationNotFoundException::new);
    }

    private static PendingSocialRegistration toPendingSocialRegistration(ExternalIdentity identity) {
        return new PendingSocialRegistration(identity.email()).name(identity.name());
    }

    private List<SocialLoginProvider> socialProviders() {
        if (!socialLoginProperties.enabled()) {
            return List.of();
        }
        return socialLoginProperties.providers().entrySet().stream()
                .map(SessionApiDelegateImpl::toSocialLoginProvider)
                .toList();
    }

    private static SocialLoginProvider toSocialLoginProvider(Map.Entry<String, ProviderConfig> provider) {
        var registrationId = provider.getKey();
        var displayName = provider.getValue().displayName();
        return new SocialLoginProvider(registrationId, StringUtils.hasText(displayName) ? displayName : registrationId);
    }
}
