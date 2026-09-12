package eu.wiegandt.librehousehold.notifications.internal;

import eu.wiegandt.librehousehold.household.AccountRegistered;
import eu.wiegandt.librehousehold.household.AccountTokenIssuer;
import eu.wiegandt.librehousehold.household.VerificationDeletionWarningRequested;
import eu.wiegandt.librehousehold.household.VerificationEmailRequested;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountRegistrationListenerTest {

    @Mock
    private AccountTokenIssuer accountTokenIssuer;

    @Mock
    private EmailSenderService emailSenderService;

    @InjectMocks
    private AccountRegistrationListener listener;

    @Nested
    class on {

        @Test
        void accountRegistered_issuesTokenAndSendsVerificationEmail() {
            // given
            var memberId = UUID.randomUUID();
            var email = "max@example.com";
            var token = UUID.randomUUID();
            doReturn(token).when(accountTokenIssuer).issueEmailVerificationToken(memberId);

            // when
            listener.on(new AccountRegistered(memberId, email));

            // then
            verify(emailSenderService).sendVerificationEmail(email, memberId, token);
        }

        @Test
        void verificationEmailRequested_issuesTokenAndSendsVerificationEmail() {
            // given
            var memberId = UUID.randomUUID();
            var email = "max@example.com";
            var token = UUID.randomUUID();
            doReturn(token).when(accountTokenIssuer).issueEmailVerificationToken(memberId);

            // when
            listener.on(new VerificationEmailRequested(memberId, email));

            // then
            verify(emailSenderService).sendVerificationEmail(email, memberId, token);
        }

        @Test
        void verificationDeletionWarningRequested_sendsDeletionWarningEmail() {
            // given
            var memberId = UUID.randomUUID();
            var email = "max@example.com";

            // when
            listener.on(new VerificationDeletionWarningRequested(memberId, email));

            // then
            verify(emailSenderService).sendVerificationDeletionWarningEmail(email, memberId);
        }
    }
}
