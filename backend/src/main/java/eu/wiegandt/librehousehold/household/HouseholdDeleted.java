package eu.wiegandt.librehousehold.household;

import java.util.UUID;

/**
 * Domain event published when a household is permanently deleted.
 * Other modules (tasks, expenses) listen to this event to clean up their own data.
 * Defined in the public package so consuming modules can reference the type.
 *
 * @param householdId the ID of the household that was deleted
 */
public record HouseholdDeleted(UUID householdId) {
}
