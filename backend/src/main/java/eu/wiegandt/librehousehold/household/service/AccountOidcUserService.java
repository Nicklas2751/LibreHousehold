package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.AccountOidcPrincipal;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * Enriches the {@link OidcUser} obtained from the embedded Authorization Server (see DD-7) with
 * {@code memberId}/{@code householdId}/{@code isAdmin}, read directly via {@link MemberRepository}
 * (same module, no Named Interface needed). The resulting {@link AccountOidcPrincipal} — not the
 * {@code AccountPrincipal} used for the internal login step — is what business API calls
 * authorize against.
 */
@Service
public class AccountOidcUserService extends OidcUserService {

    private final MemberRepository memberRepository;

    public AccountOidcUserService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public @NonNull OidcUser loadUser(@NonNull OidcUserRequest userRequest) {
        return enrich(super.loadUser(userRequest));
    }

    AccountOidcPrincipal enrich(OidcUser oidcUser) {
        var member = memberRepository.findByEmail(oidcUser.getSubject())
                .orElseThrow(() -> new OAuth2AuthenticationException(
                        new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "No member found for the authenticated subject", null)));
        return new AccountOidcPrincipal(oidcUser, member.getId(), member.householdId(), member.isAdmin());
    }
}
