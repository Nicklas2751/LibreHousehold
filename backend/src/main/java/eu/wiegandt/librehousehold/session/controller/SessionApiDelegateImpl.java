package eu.wiegandt.librehousehold.session.controller;

import eu.wiegandt.librehousehold.api.SessionApiDelegate;
import eu.wiegandt.librehousehold.household.AccountOidcPrincipal;
import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.household.PasswordReset;
import eu.wiegandt.librehousehold.model.CurrentUser;
import eu.wiegandt.librehousehold.model.PasswordResetConfirm;
import eu.wiegandt.librehousehold.model.PasswordResetRequest;
import eu.wiegandt.librehousehold.session.exception.NoAuthenticatedSessionException;
import eu.wiegandt.librehousehold.usersettings.PreferencesQuery;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Handles {@code GET /me} (see P1.4.7) and the password-reset endpoints. {@code POST /logout} is
 * deliberately not implemented here: it is fully handled by Spring Security's own {@code logout()}
 * filter (see {@code SecurityConfig}/{@code RevokeAuthorizedClientLogoutHandler}), which intercepts
 * the request before it would ever reach this controller.
 *
 * <p>The password-reset endpoints share the OpenAPI {@code session} tag (and therefore this single
 * generated delegate) even though their business logic belongs to {@code household}. Delegated to
 * via the {@link PasswordReset} Named Interface rather than household's internal service classes,
 * since this class lives in the {@code session} module (see ADR-011).
 */
@Component
public class SessionApiDelegateImpl implements SessionApiDelegate {

    private final MemberQuery memberQuery;
    private final HouseholdQuery householdQuery;
    private final PreferencesQuery preferencesQuery;
    private final PasswordReset passwordReset;

    public SessionApiDelegateImpl(MemberQuery memberQuery, HouseholdQuery householdQuery,
                                   PreferencesQuery preferencesQuery, PasswordReset passwordReset) {
        this.memberQuery = memberQuery;
        this.householdQuery = householdQuery;
        this.preferencesQuery = preferencesQuery;
        this.passwordReset = passwordReset;
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
}
