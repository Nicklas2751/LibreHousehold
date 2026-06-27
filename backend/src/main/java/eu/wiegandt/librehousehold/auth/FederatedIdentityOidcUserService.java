package eu.wiegandt.librehousehold.auth;

import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
class FederatedIdentityOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate = new OidcUserService();
    private final JitProvisioningHandler jitProvisioningHandler;

    FederatedIdentityOidcUserService(JitProvisioningHandler jitProvisioningHandler) {
        this.jitProvisioningHandler = jitProvisioningHandler;
    }

    @Override
    public OidcUser loadUser(@NonNull OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        var oidcUser = delegate.loadUser(userRequest);
        jitProvisioningHandler.provision(
                userRequest.getClientRegistration().getRegistrationId(),
                oidcUser.getSubject(),
                oidcUser.getEmail());
        // OidcAuthorizationCodeAuthenticationProvider does not add FactorGrantedAuthority,
        // which Spring Authorization Server 7.1 requires to determine auth_time for JWT generation.
        List<GrantedAuthority> authorities = new ArrayList<>(oidcUser.getAuthorities());
        authorities.add(FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.AUTHORIZATION_CODE_AUTHORITY));
        return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
    }
}
