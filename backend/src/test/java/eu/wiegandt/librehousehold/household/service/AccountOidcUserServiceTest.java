package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.AccountOidcPrincipal;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class AccountOidcUserServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private AccountOidcUserService accountOidcUserService;

    @Test
    void enrich_oidcUserWithMemberEmail_returnsAccountOidcPrincipalWithMemberData() {
        // given
        var email = "max@example.com";
        var memberId = UUID.randomUUID();
        var householdId = UUID.randomUUID();
        var oidcUser = oidcUserWithSubject(email);
        var member = new MemberEntity(memberId, "Max", email, null, householdId, true);
        var expectedPrincipal = new AccountOidcPrincipal(oidcUser, memberId, householdId, true);
        doReturn(Optional.of(member)).when(memberRepository).findByEmail(email);

        // when
        var result = accountOidcUserService.enrich(oidcUser);

        // then
        assertThat(result).usingRecursiveComparison().isEqualTo(expectedPrincipal);
    }

    @Test
    void enrich_noMemberForSubjectEmail_throwsOAuth2AuthenticationException() {
        // given
        var email = "unknown@example.com";
        var oidcUser = oidcUserWithSubject(email);
        doReturn(Optional.empty()).when(memberRepository).findByEmail(email);

        // when / then
        assertThatThrownBy(() -> accountOidcUserService.enrich(oidcUser))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    private OidcUser oidcUserWithSubject(String email) {
        var idToken = new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(300),
                Map.of(IdTokenClaimNames.SUB, email, IdTokenClaimNames.ISS, "http://localhost/issuer"));
        return new DefaultOidcUser(List.of(), idToken);
    }
}
