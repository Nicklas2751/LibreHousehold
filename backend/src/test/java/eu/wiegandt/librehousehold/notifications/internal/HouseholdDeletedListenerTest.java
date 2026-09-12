package eu.wiegandt.librehousehold.notifications.internal;

import eu.wiegandt.librehousehold.household.HouseholdDeleted;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HouseholdDeletedListenerTest {

    @Mock
    private EmailSenderService emailSenderService;

    @InjectMocks
    private HouseholdDeletedListener householdDeletedListener;

    @Test
    void on_householdDeletedWithThreeMembers_sendsThreeEmails() {
        // given
        var householdId = UUID.randomUUID();
        var householdName = "Musterhaushalt";
        var firstMember = new HouseholdDeleted.DeletedMember(UUID.randomUUID(), "Anna", "anna@example.com");
        var secondMember = new HouseholdDeleted.DeletedMember(UUID.randomUUID(), "Bea", "bea@example.com");
        var thirdMember = new HouseholdDeleted.DeletedMember(UUID.randomUUID(), "Carl", "carl@example.com");
        var event = new HouseholdDeleted(householdId, householdName, List.of(firstMember, secondMember, thirdMember));

        // when
        householdDeletedListener.on(event);

        // then
        verify(emailSenderService).sendHouseholdDeletedEmail(firstMember.email(), firstMember.memberId(),
                firstMember.name(), householdName);
        verify(emailSenderService).sendHouseholdDeletedEmail(secondMember.email(), secondMember.memberId(),
                secondMember.name(), householdName);
        verify(emailSenderService).sendHouseholdDeletedEmail(thirdMember.email(), thirdMember.memberId(),
                thirdMember.name(), householdName);
    }
}
