package eu.wiegandt.librehousehold.session.controller;

import eu.wiegandt.librehousehold.api.SessionApiDelegate;
import eu.wiegandt.librehousehold.household.AccountOidcPrincipal;
import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.model.CurrentUser;
import eu.wiegandt.librehousehold.usersettings.PreferencesQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Handles {@code GET /me} (see P1.4.7). {@code POST /logout} is deliberately not implemented here:
 * it is fully handled by Spring Security's own {@code logout()} filter (see
 * {@code SecurityConfig}/{@code RevokeAuthorizedClientLogoutHandler}), which intercepts the request
 * before it would ever reach this controller.
 */
@Component
public class SessionApiDelegateImpl implements SessionApiDelegate {

    private final MemberQuery memberQuery;
    private final PreferencesQuery preferencesQuery;

    public SessionApiDelegateImpl(MemberQuery memberQuery, PreferencesQuery preferencesQuery) {
        this.memberQuery = memberQuery;
        this.preferencesQuery = preferencesQuery;
    }

    @Override
    public ResponseEntity<CurrentUser> getCurrentUser() {
        var principal = (AccountOidcPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        var member = memberQuery.getMember(principal.memberId());
        var preferences = preferencesQuery.getPreferencesOrDefault(principal.memberId());
        return ResponseEntity.ok(new CurrentUser(member, principal.householdId(), preferences));
    }
}
