package eu.wiegandt.librehousehold.notifications.internal;

import eu.wiegandt.librehousehold.household.MemberRemoved;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to member removals (self-service leave, admin-initiated removal, or automatic removal
 * after an expired verification grace period) by notifying the removed member (see ADR-011:
 * {@code household} publishes the event without knowing that {@code notifications} exists; this
 * listener is the sole consumer).
 */
@Component
public class MemberRemovedListener {

    private final EmailSenderService emailSenderService;

    public MemberRemovedListener(EmailSenderService emailSenderService) {
        this.emailSenderService = emailSenderService;
    }

    @ApplicationModuleListener
    public void on(MemberRemoved event) {
        emailSenderService.sendMemberRemovedEmail(event.email(), event.memberId(), event.memberName(),
                event.householdName());
    }
}
