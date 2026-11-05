package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.AccountPrincipal;
import eu.wiegandt.librehousehold.household.exception.AccountTokenInvalidException;
import eu.wiegandt.librehousehold.household.exception.PasswordResetTokenInvalidException;
import eu.wiegandt.librehousehold.household.repository.AccountRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String NEW_PASSWORD = "new correct horse battery staple";

    @Mock
    private MemberManagementService memberManagementService;

    @Mock
    private AccountTokenService accountTokenService;

    @Mock
    private AccountService accountService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SessionRegistry sessionRegistry;

    // MemberSessions is a module-internal collaborator, so it runs for real on top of the mocked registry
    private PasswordResetService newService() {
        return new PasswordResetService(memberManagementService, accountTokenService, accountService,
                accountRepository, new MemberSessions(sessionRegistry));
    }

    @Nested
    class requestPasswordReset {

        @Test
        void email_delegatesToMemberManagementService() {
            // given
            var email = "max@example.com";

            // when
            newService().requestPasswordReset(email);

            // then
            verify(memberManagementService).requestPasswordReset(email);
        }
    }

    @Nested
    class confirmPasswordReset {

        @Test
        void validToken_expiresSessionsOfMember() {
            // given
            var token = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            var session = new SessionInformation(AccountPrincipal.identifiedBy(memberId), "session-1",
                    Date.from(Instant.now()));
            doReturn(memberId).when(accountTokenService).consumeToken(token, AccountTokenService.PASSWORD_RESET_PURPOSE);
            doReturn(true).when(accountService).hasPassword(memberId);
            doReturn(List.of(session)).when(sessionRegistry).getAllSessions(AccountPrincipal.identifiedBy(memberId), false);

            // when
            newService().confirmPasswordReset(token, NEW_PASSWORD);

            // then
            assertThat(session.isExpired()).isTrue();
        }

        @Test
        void validToken_resetsPassword() {
            // given
            var token = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            doReturn(memberId).when(accountTokenService).consumeToken(token, AccountTokenService.PASSWORD_RESET_PURPOSE);
            doReturn(true).when(accountService).hasPassword(memberId);

            // when
            newService().confirmPasswordReset(token, NEW_PASSWORD);

            // then
            verify(accountService).resetPassword(memberId, NEW_PASSWORD);
        }

        @Test
        void validToken_resetsLockout() {
            // given
            var token = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            doReturn(memberId).when(accountTokenService).consumeToken(token, AccountTokenService.PASSWORD_RESET_PURPOSE);
            doReturn(true).when(accountService).hasPassword(memberId);

            // when
            newService().confirmPasswordReset(token, NEW_PASSWORD);

            // then
            verify(accountRepository).resetFailedLoginAttempts(memberId);
        }

        @Test
        void invalidToken_throwsPasswordResetTokenInvalidException() {
            // given
            var token = UUID.randomUUID();
            doThrow(AccountTokenInvalidException.class).when(accountTokenService)
                    .consumeToken(token, AccountTokenService.PASSWORD_RESET_PURPOSE);

            // when / then
            assertThatThrownBy(() -> newService().confirmPasswordReset(token, NEW_PASSWORD))
                    .isInstanceOf(PasswordResetTokenInvalidException.class);
        }

        @Test
        void invalidToken_changesNothing() {
            // given
            var token = UUID.randomUUID();
            doThrow(AccountTokenInvalidException.class).when(accountTokenService)
                    .consumeToken(token, AccountTokenService.PASSWORD_RESET_PURPOSE);

            // when
            catchThrowable(() -> newService().confirmPasswordReset(token, NEW_PASSWORD));

            // then
            verifyNoInteractions(accountService, accountRepository, sessionRegistry);
        }

        @Test
        void socialLoginOnlyAccount_throwsPasswordResetTokenInvalidException() {
            // given
            var token = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            doReturn(memberId).when(accountTokenService).consumeToken(token, AccountTokenService.PASSWORD_RESET_PURPOSE);
            doReturn(false).when(accountService).hasPassword(memberId);

            // when / then
            assertThatThrownBy(() -> newService().confirmPasswordReset(token, NEW_PASSWORD))
                    .isInstanceOf(PasswordResetTokenInvalidException.class);
        }

        @Test
        void socialLoginOnlyAccount_doesNotResetPassword() {
            // given
            var token = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            doReturn(memberId).when(accountTokenService).consumeToken(token, AccountTokenService.PASSWORD_RESET_PURPOSE);
            doReturn(false).when(accountService).hasPassword(memberId);

            // when
            catchThrowable(() -> newService().confirmPasswordReset(token, NEW_PASSWORD));

            // then
            verify(accountService, never()).resetPassword(any(), any());
        }

        @Test
        void socialLoginOnlyAccount_neitherResetsLockoutNorTouchesSessions() {
            // given
            var token = UUID.randomUUID();
            var memberId = UUID.randomUUID();
            doReturn(memberId).when(accountTokenService).consumeToken(token, AccountTokenService.PASSWORD_RESET_PURPOSE);
            doReturn(false).when(accountService).hasPassword(memberId);

            // when
            catchThrowable(() -> newService().confirmPasswordReset(token, NEW_PASSWORD));

            // then
            verifyNoInteractions(accountRepository, sessionRegistry);
        }
    }
}
