package eu.wiegandt.librehousehold.auth;

import eu.wiegandt.librehousehold.core.ResourceOwnerQuery;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
class AuthorChecker {

    private final List<ResourceOwnerQuery> ownerQueries;

    AuthorChecker(List<ResourceOwnerQuery> ownerQueries) {
        this.ownerQueries = ownerQueries;
    }

    public boolean isAuthor(UUID resourceId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            throw new AuthorizationDeniedException("Authentication is not a JWT token");
        }
        var subject = jwtAuth.getToken().getSubject();
        if (subject == null) {
            throw new AuthorizationDeniedException("JWT token has no subject claim");
        }
        var accountId = UUID.fromString(subject);
        return ownerQueries.stream().anyMatch(resourceOwnerQuery -> resourceOwnerQuery.isOwner(resourceId, accountId));
    }
}
