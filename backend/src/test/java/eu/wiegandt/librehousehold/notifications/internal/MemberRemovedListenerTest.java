package eu.wiegandt.librehousehold.notifications.internal;

import eu.wiegandt.librehousehold.household.MemberRemoved;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberRemovedListenerTest {

    @Mock
    private EmailSenderService emailSenderService;

    @InjectMocks
    private MemberRemovedListener memberRemovedListener;

    @Test
    void on_memberRemoved_sendsOneEmail() {
        // given
        var memberId = UUID.randomUUID();
        var memberName = "Anna";
        var email = "anna@example.com";
        var householdName = "Musterhaushalt";
        var event = new MemberRemoved(memberId, memberName, email, householdName);

        // when
        memberRemovedListener.on(event);

        // then
        verify(emailSenderService).sendMemberRemovedEmail(email, memberId, memberName, householdName);
    }
}
