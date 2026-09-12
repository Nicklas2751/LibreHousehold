package eu.wiegandt.librehousehold.notifications.internal;

import eu.wiegandt.librehousehold.household.HouseholdDeleted;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to household deletions by notifying every member who belonged to the household at the
 * time of deletion, including the admin who triggered it (see ADR-011: {@code household} publishes
 * the event without knowing that {@code notifications} exists; this listener is the sole consumer).
 */
@Component
public class HouseholdDeletedListener {

    private final EmailSenderService emailSenderService;

    public HouseholdDeletedListener(EmailSenderService emailSenderService) {
        this.emailSenderService = emailSenderService;
    }

    @ApplicationModuleListener
    public void on(HouseholdDeleted event) {
        event.members().forEach(member -> emailSenderService.sendHouseholdDeletedEmail(member.email(),
                member.memberId(), member.name(), event.householdName()));
    }
}
