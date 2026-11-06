package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.AccountPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AccountSessionSignInTest {

    private static final String PROVIDER_ID = "https://accounts.google.com";
    private static final String SUBJECT = "external-subject";

    @Mock
    private AccountUserDetailsService accountUserDetailsService;

    @Mock
    private AccountService accountService;

    @Mock
    private SessionAuthenticationStrategy sessionAuthenticationStrategy;

    @Mock
    private SessionRegistry sessionRegistry;

    @InjectMocks
    private AccountSessionSignIn accountSessionSignIn;

    @AfterEach
    void clearThreadLocalState() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    class signIn {

        @Test
        void existingMember_contextHoldsExpectedPrincipal() {
            // given
            var memberId = UUID.randomUUID();
            var expectedPrincipal = AccountPrincipal.identifiedBy(memberId);
            doReturn(new AccountPrincipal(memberId, UUID.randomUUID(), "$argon2id$...", false))
                    .when(accountUserDetailsService).loadUserByMemberId(memberId);
            bindRequest(new MockHttpServletRequest());

            // when
            accountSessionSignIn.signIn(memberId);

            // then
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(expectedPrincipal);
        }

        @Test
        void existingMember_principalWithoutPasswordHash() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(new AccountPrincipal(memberId, UUID.randomUUID(), "$argon2id$...", true))
                    .when(accountUserDetailsService).loadUserByMemberId(memberId);
            bindRequest(new MockHttpServletRequest());

            // when
            accountSessionSignIn.signIn(memberId);

            // then
            assertThat(((AccountPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                    .getPassword()).isNull();
        }

        @Test
        void existingMember_invokesSessionAuthenticationStrategy() {
            // given
            var memberId = UUID.randomUUID();
            var request = new MockHttpServletRequest();
            doReturn(new AccountPrincipal(memberId, UUID.randomUUID(), "$argon2id$...", false))
                    .when(accountUserDetailsService).loadUserByMemberId(memberId);
            var response = bindRequest(request);

            // when
            accountSessionSignIn.signIn(memberId);

            // then
            verify(sessionAuthenticationStrategy).onAuthentication(
                    argThat(authentication -> AccountPrincipal.identifiedBy(memberId).equals(authentication.getPrincipal())),
                    eq(request), eq(response));
        }

        @Test
        void unknownMember_throws() {
            // given
            var memberId = UUID.randomUUID();
            doThrow(new UsernameNotFoundException("unknown")).when(accountUserDetailsService).loadUserByMemberId(memberId);
            bindRequest(new MockHttpServletRequest());

            // when / then
            assertThatThrownBy(() -> accountSessionSignIn.signIn(memberId)).isInstanceOf(UsernameNotFoundException.class);
        }

        @Test
        void noResponseInRequestContext_throwsIllegalStateException() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(new AccountPrincipal(memberId, UUID.randomUUID(), "$argon2id$...", false))
                    .when(accountUserDetailsService).loadUserByMemberId(memberId);
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

            // when / then
            assertThatIllegalStateException().isThrownBy(() -> accountSessionSignIn.signIn(memberId));
        }
    }

    @Nested
    class signInByExternalIdentity {

        @Test
        void linkedIdentity_returnsExpectedMemberId() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(Optional.of(memberId)).when(accountService).findMemberIdByExternalIdentity(PROVIDER_ID, SUBJECT);
            doReturn(new AccountPrincipal(memberId, UUID.randomUUID(), null, true))
                    .when(accountUserDetailsService).loadUserByMemberId(memberId);
            bindRequest(new MockHttpServletRequest());

            // when
            var result = accountSessionSignIn.signInByExternalIdentity(PROVIDER_ID, SUBJECT);

            // then
            assertThat(result).contains(memberId);
        }

        @Test
        void linkedIdentity_contextHoldsExpectedPrincipal() {
            // given
            var memberId = UUID.randomUUID();
            var expectedPrincipal = AccountPrincipal.identifiedBy(memberId);
            doReturn(Optional.of(memberId)).when(accountService).findMemberIdByExternalIdentity(PROVIDER_ID, SUBJECT);
            doReturn(new AccountPrincipal(memberId, UUID.randomUUID(), null, true))
                    .when(accountUserDetailsService).loadUserByMemberId(memberId);
            bindRequest(new MockHttpServletRequest());

            // when
            accountSessionSignIn.signInByExternalIdentity(PROVIDER_ID, SUBJECT);

            // then
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(expectedPrincipal);
        }

        @Test
        void linkedIdentity_registersSessionForPrincipal() {
            // given
            var memberId = UUID.randomUUID();
            var request = new MockHttpServletRequest();
            doReturn(Optional.of(memberId)).when(accountService).findMemberIdByExternalIdentity(PROVIDER_ID, SUBJECT);
            doReturn(new AccountPrincipal(memberId, UUID.randomUUID(), null, true))
                    .when(accountUserDetailsService).loadUserByMemberId(memberId);
            bindRequest(request);

            // when
            accountSessionSignIn.signInByExternalIdentity(PROVIDER_ID, SUBJECT);

            // then
            verify(sessionRegistry).registerNewSession(request.getSession().getId(), AccountPrincipal.identifiedBy(memberId));
        }

        @Test
        void linkedIdentity_doesNotChangeSessionIdAgain() {
            // given
            var memberId = UUID.randomUUID();
            doReturn(Optional.of(memberId)).when(accountService).findMemberIdByExternalIdentity(PROVIDER_ID, SUBJECT);
            doReturn(new AccountPrincipal(memberId, UUID.randomUUID(), null, true))
                    .when(accountUserDetailsService).loadUserByMemberId(memberId);
            bindRequest(new MockHttpServletRequest());

            // when
            accountSessionSignIn.signInByExternalIdentity(PROVIDER_ID, SUBJECT);

            // then
            verifyNoInteractions(sessionAuthenticationStrategy);
        }

        @Test
        void unknownIdentity_returnsEmpty() {
            // given
            doReturn(Optional.empty()).when(accountService).findMemberIdByExternalIdentity(PROVIDER_ID, SUBJECT);

            // when
            var result = accountSessionSignIn.signInByExternalIdentity(PROVIDER_ID, SUBJECT);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void unknownIdentity_leavesContextEmpty() {
            // given
            doReturn(Optional.empty()).when(accountService).findMemberIdByExternalIdentity(PROVIDER_ID, SUBJECT);

            // when
            accountSessionSignIn.signInByExternalIdentity(PROVIDER_ID, SUBJECT);

            // then
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    private MockHttpServletResponse bindRequest(MockHttpServletRequest request) {
        var response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        return response;
    }
}
