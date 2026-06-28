package eu.wiegandt.librehousehold.auth;

import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class HouseholdScopeChecker {

    public boolean isCurrentUserInHousehold(UUID householdId) {
        var jwtHouseholdId = resolveHouseholdIdClaim();
        if (jwtHouseholdId == null) {
            throw new AuthorizationDeniedException("JWT is missing the household_id claim");
        }
        return UUID.fromString(jwtHouseholdId).equals(householdId);
    }

    private String resolveHouseholdIdClaim() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getClaimAsString("household_id");
        }
        throw new AuthorizationDeniedException("Authentication is not a JWT token");
    }
}
