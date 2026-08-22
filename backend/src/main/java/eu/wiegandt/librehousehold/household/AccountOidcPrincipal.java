package eu.wiegandt.librehousehold.household;

import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public record AccountOidcPrincipal(OidcUser oidcUser, UUID memberId, UUID householdId, boolean isAdmin)
        implements OidcUser {

    @Override
    public @NonNull Map<String, Object> getClaims() {
        return oidcUser.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return oidcUser.getUserInfo();
    }

    @Override
    public @NonNull OidcIdToken getIdToken() {
        return oidcUser.getIdToken();
    }

    @Override
    public @NonNull Map<String, Object> getAttributes() {
        return oidcUser.getAttributes();
    }

    @Override
    public @NonNull Collection<? extends GrantedAuthority> getAuthorities() {
        return oidcUser.getAuthorities();
    }

    @Override
    public @NonNull String getName() {
        return oidcUser.getName();
    }
}
