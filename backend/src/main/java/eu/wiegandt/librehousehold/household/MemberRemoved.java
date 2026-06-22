package eu.wiegandt.librehousehold.household;

import java.util.UUID;

/**
 * Domain event published when a member is permanently removed from a household.
 * Other modules listen to this event to clean up member-specific data.
 * Defined in the public package so consuming modules can reference the type.
 *
 * @param memberId the ID of the member who was removed
 */
public record MemberRemoved(UUID memberId) {}
