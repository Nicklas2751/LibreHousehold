package eu.wiegandt.librehousehold.auth;

import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
class AdminChecker {

    private static final String ROLE_ADMIN = "admin";
    private static final String JWT_CLAIM_ROLE = "role";

    public boolean isAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return ROLE_ADMIN.equals(jwtAuth.getToken().getClaimAsString(JWT_CLAIM_ROLE));
        }
        throw new AuthorizationDeniedException("Authentication is not a JWT token");
    }
}
