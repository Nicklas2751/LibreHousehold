package eu.wiegandt.librehousehold.auth;

import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentUserIdProvider {

    public UUID getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return UUID.fromString(jwtAuth.getToken().getSubject());
        }
        throw new AuthorizationDeniedException("Authentication is not a JWT token");
    }
}
