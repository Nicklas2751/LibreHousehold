package eu.wiegandt.librehousehold.session.controller;

import eu.wiegandt.librehousehold.api.SessionApiDelegate;
import eu.wiegandt.librehousehold.household.LocalLoginProperties;
import eu.wiegandt.librehousehold.config.SocialLoginContextStore;
import eu.wiegandt.librehousehold.household.AccountPrincipal;
import eu.wiegandt.librehousehold.household.ExternalIdentity;
import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.household.LocalLoginDisabledException;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Handles {@code GET /me}, the password-reset endpoints, storing the social-login context and the
 * login-method discovery. {@code POST /login} and {@code POST /logout} are handled by Spring
 * Security's form login and logout filters (see {@code SecurityConfig}), which intercept the
 * request before it reaches this controller. {@code login} is therefore only reached when this
 * instance turned local login off, and rejects the request.
 *
 * <p>{@code GET /me} also reports {@code server.servlet.session.timeout}: every reconnect of the event
 * stream is a request that keeps the session alive, so the client needs the timeout to close its
 * stream once the user has been idle for nearly that long (ADR-019).
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
 * offers, read from {@link LocalLoginProperties} and the social-login registrations Spring Boot
 * built from {@code spring.security.oauth2.client.*} (none configured: no repository). Only the
 * registration ID and client name of each provider leave the server, never client credentials or
 * endpoint configuration.
 */
@Component
public class SessionApiDelegateImpl implements SessionApiDelegate {

    private final MemberQuery memberQuery;
    private final HouseholdQuery householdQuery;
    private final PreferencesQuery preferencesQuery;
    private final PasswordReset passwordReset;
    private final SocialLoginContextStore socialLoginContextStore;
    private final ObjectProvider<InMemoryClientRegistrationRepository> clientRegistrationRepository;
    private final LocalLoginProperties localLoginProperties;
    private final PendingSocialRegistrationStore pendingSocialRegistrationStore;
    private final Duration sessionTimeout;

    public SessionApiDelegateImpl(MemberQuery memberQuery, HouseholdQuery householdQuery,
                                   PreferencesQuery preferencesQuery, PasswordReset passwordReset,
                                   SocialLoginContextStore socialLoginContextStore,
                                   ObjectProvider<InMemoryClientRegistrationRepository> clientRegistrationRepository,
                                   LocalLoginProperties localLoginProperties,
                                   PendingSocialRegistrationStore pendingSocialRegistrationStore,
                                   @Value("${server.servlet.session.timeout}") Duration sessionTimeout) {
        this.memberQuery = memberQuery;
        this.householdQuery = householdQuery;
        this.preferencesQuery = preferencesQuery;
        this.passwordReset = passwordReset;
        this.socialLoginContextStore = socialLoginContextStore;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.localLoginProperties = localLoginProperties;
        this.pendingSocialRegistrationStore = pendingSocialRegistrationStore;
        this.sessionTimeout = sessionTimeout;
    }

    @Override
    public ResponseEntity<CurrentUser> getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            throw new NoAuthenticatedSessionException();
        }
        var member = memberQuery.getMember(principal.memberId());
        var household = householdQuery.getHousehold(principal.householdId());
        var preferences = preferencesQuery.getPreferencesOrDefault(principal.memberId());
        var emailVerified = memberQuery.isEmailVerified(principal.memberId());
        var socialLoginOnly = memberQuery.isSocialLoginOnly(principal.memberId());
        return ResponseEntity.ok(new CurrentUser(member, household, preferences, emailVerified, socialLoginOnly,
                Math.toIntExact(sessionTimeout.toSeconds())));
    }

    @Override
    public ResponseEntity<Void> login(String email, String password) {
        throw new LocalLoginDisabledException();
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
        var registrations = clientRegistrationRepository.getIfAvailable();
        if (registrations == null) {
            return List.of();
        }
        return StreamSupport.stream(registrations.spliterator(), false)
                .map(SessionApiDelegateImpl::toSocialLoginProvider)
                .toList();
    }

    // ClientRegistration falls back to the registration ID when no client-name is configured
    private static SocialLoginProvider toSocialLoginProvider(ClientRegistration registration) {
        return new SocialLoginProvider(registration.getRegistrationId(), registration.getClientName());
    }
}
