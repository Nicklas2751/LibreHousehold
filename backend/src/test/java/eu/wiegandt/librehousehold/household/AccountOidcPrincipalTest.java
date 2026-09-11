package eu.wiegandt.librehousehold.household;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountOidcPrincipalTest {

    /**
     * Regression guard for the {@code authenticationTime cannot be null} crash (see this class's
     * Javadoc): the wrapped {@code oidcUser}'s own authorities never carry a
     * {@link FactorGrantedAuthority}, so {@code getAuthorities()} must add one on top.
     */
    @Test
    void getAuthorities_wrappedOidcUserWithoutFactorAuthority_addsPasswordFactorAuthority() {
        // given
        var idToken = new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(300),
                Map.of(IdTokenClaimNames.SUB, "max@example.com", IdTokenClaimNames.ISS, "http://localhost/issuer"));
        var oidcUser = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);
        var principal = new AccountOidcPrincipal(oidcUser, UUID.randomUUID(), UUID.randomUUID(), true);

        // when
        var result = principal.getAuthorities();

        // then
        assertThat(result).anyMatch(authority -> authority instanceof FactorGrantedAuthority factorGrantedAuthority
                && factorGrantedAuthority.getAuthority().equals(FactorGrantedAuthority.PASSWORD_AUTHORITY));
    }
}
