package eu.wiegandt.librehousehold.notifications.internal;

import eu.wiegandt.librehousehold.household.AccountTokenIssuer;
import eu.wiegandt.librehousehold.household.PasswordResetRequested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordResetRequestedListenerTest {

    @Mock
    private AccountTokenIssuer accountTokenIssuer;

    @Mock
    private EmailSenderService emailSenderService;

    @InjectMocks
    private PasswordResetRequestedListener listener;

    @Test
    void on_passwordResetRequested_issuesTokenAndSendsPasswordResetEmail() {
        // given
        var memberId = UUID.randomUUID();
        var email = "max@example.com";
        var token = UUID.randomUUID();
        doReturn(token).when(accountTokenIssuer).issuePasswordResetToken(memberId);

        // when
        listener.on(new PasswordResetRequested(memberId, email));

        // then
        verify(emailSenderService).sendPasswordResetEmail(email, memberId, token);
    }
}
