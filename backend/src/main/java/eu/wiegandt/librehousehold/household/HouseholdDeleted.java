package eu.wiegandt.librehousehold.household;

import java.util.List;
import java.util.UUID;

/**
 * Domain event published when a household is permanently deleted.
 * Other modules (tasks, expenses) listen to this event to clean up their own data.
 * Defined in the public package so consuming modules can reference the type.
 *
 * <p>The household name and member data are carried by the event itself, since the
 * household and its members are already deleted from the database by the time an
 * {@code @ApplicationModuleListener} (which runs {@code AFTER_COMMIT}) reacts to it.
 *
 * @param householdId   the ID of the household that was deleted
 * @param householdName the name of the household that was deleted
 * @param members       the members who belonged to the household at the time of deletion
 */
public record HouseholdDeleted(UUID householdId, String householdName, List<DeletedMember> members) {

    public record DeletedMember(UUID memberId, String name, String email) {
    }
}
