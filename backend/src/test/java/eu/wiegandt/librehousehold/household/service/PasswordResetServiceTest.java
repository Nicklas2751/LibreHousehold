package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.AccountPrincipal;
import eu.wiegandt.librehousehold.household.exception.AccountTokenInvalidException;
import eu.wiegandt.librehousehold.household.exception.PasswordResetTokenInvalidException;
import eu.wiegandt.librehousehold.household.model.AccountEntity;
import eu.wiegandt.librehousehold.household.model.MemberEntity;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import eu.wiegandt.librehousehold.household.repository.MemberRepository;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, InstancioExtension.class})
class PasswordResetServiceTest {

    private static final String CLIENT_REGISTRATION_ID = "spa-backend-client";

    @Mock
    private MemberManagementService memberManagementService;

    @Mock
    private AccountTokenService accountTokenService;

    @Mock
    private AccountService accountService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SessionRegistry sessionRegistry;

    @Mock
    private OAuth2AuthorizedClientService authorizedClientService;

    @InjectMocks
    private PasswordResetService service;

    @Nested
    class requestPasswordReset {

        @Test
        void email_delegatesToMemberManagementService() {
            // given
            var email = "max@example.com";

            // when
            service.requestPasswordReset(email);

            // then
            verify(memberManagementService).requestPasswordReset(email);
        }
    }

    @Nested
    class confirmPasswordReset {

        @Test
        void validToken_resetsPasswordExpiresSessionsAndRemovesAuthorizedClient() {
            // given
            var token = UUID.randomUUID();
            var newPassword = "new correct horse battery staple";
            var member = Instancio.create(MemberEntity.class);
            var account = Instancio.of(AccountEntity.class)
                    .set(field(AccountEntity::memberId), member.getId())
                    .create();
            doReturn(member.getId()).when(accountTokenService)
                    .consumeToken(token, AccountTokenService.PASSWORD_RESET_PURPOSE);
            doReturn(Optional.of(member)).when(memberRepository).findById(member.getId());
            doReturn(Optional.of(account)).when(accountRepository).findById(member.getId());
            var principal = new AccountPrincipal(member.email(), account.passwordHash(), account.emailVerified());
            var session = new SessionInformation(principal, "session-1", Date.from(Instant.now()));
            doReturn(List.of(session)).when(sessionRegistry).getAllSessions(principal, false);

            // when
            service.confirmPasswordReset(token, newPassword);

            // then
            assertThat(session.isExpired()).isTrue();
        }

        @Test
        void validToken_resetsPassword() {
            // given
            var token = UUID.randomUUID();
            var newPassword = "new correct horse battery staple";
            var member = Instancio.create(MemberEntity.class);
            var account = Instancio.of(AccountEntity.class)
                    .set(field(AccountEntity::memberId), member.getId())
                    .create();
            doReturn(member.getId()).when(accountTokenService)
                    .consumeToken(token, AccountTokenService.PASSWORD_RESET_PURPOSE);
            doReturn(Optional.of(member)).when(memberRepository).findById(member.getId());
            doReturn(Optional.of(account)).when(accountRepository).findById(member.getId());
            doReturn(List.of()).when(sessionRegistry).getAllSessions(any(), anyBoolean());

            // when
            service.confirmPasswordReset(token, newPassword);

            // then
            verify(accountService).resetPassword(member.getId(), newPassword);
        }

        @Test
        void validToken_removesAuthorizedClientForMemberEmail() {
            // given
            var token = UUID.randomUUID();
            var newPassword = "new correct horse battery staple";
            var member = Instancio.create(MemberEntity.class);
            var account = Instancio.of(AccountEntity.class)
                    .set(field(AccountEntity::memberId), member.getId())
                    .create();
            doReturn(member.getId()).when(accountTokenService)
                    .consumeToken(token, AccountTokenService.PASSWORD_RESET_PURPOSE);
            doReturn(Optional.of(member)).when(memberRepository).findById(member.getId());
            doReturn(Optional.of(account)).when(accountRepository).findById(member.getId());
            doReturn(List.of()).when(sessionRegistry).getAllSessions(any(), anyBoolean());

            // when
            service.confirmPasswordReset(token, newPassword);

            // then
            verify(authorizedClientService).removeAuthorizedClient(CLIENT_REGISTRATION_ID, member.email());
        }

        @Test
        void invalidToken_throwsPasswordResetTokenInvalidExceptionWithoutResettingPassword() {
            // given
            var token = UUID.randomUUID();
            doThrow(AccountTokenInvalidException.class).when(accountTokenService)
                    .consumeToken(token, AccountTokenService.PASSWORD_RESET_PURPOSE);

            // when / then
            assertThatThrownBy(() -> service.confirmPasswordReset(token, "newPassword"))
                    .isInstanceOf(PasswordResetTokenInvalidException.class);
            verify(accountService, never()).resetPassword(any(), any());
        }
    }
}
