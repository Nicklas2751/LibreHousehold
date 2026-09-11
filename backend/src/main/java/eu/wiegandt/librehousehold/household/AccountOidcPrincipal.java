package eu.wiegandt.librehousehold.household;

import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

/**
 * {@code getAuthorities()} adds a {@link FactorGrantedAuthority} on top of the wrapped
 * {@code oidcUser}'s own authorities (which never carry one): {@code JwtGenerator.getAuthenticationTime}
 * requires at least one {@link FactorGrantedAuthority} on the resource-owner {@code Authentication}
 * whenever a new {@code /oauth2/authorize} request is served, and this principal — once installed as
 * the session's {@code OAuth2AuthenticationToken} after a completed {@code oauth2Login()} — is exactly
 * that {@code Authentication} for any *later* re-authorization on the same session (e.g. the SPA
 * re-triggering its own {@code /oauth2/authorization/spa-backend-client} continuation). Without this,
 * such a re-authorization throws {@code IllegalArgumentException: authenticationTime cannot be null}
 * at token-exchange time — the same failure mode already fixed once for {@code AccountSessionAuthenticator}
 * (see its Javadoc), reappearing through this different code path.
 */
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
        var authorities = new LinkedHashSet<GrantedAuthority>(oidcUser.getAuthorities());
        authorities.add(FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY));
        return authorities;
    }

    @Override
    public @NonNull String getName() {
        return oidcUser.getName();
    }
}
