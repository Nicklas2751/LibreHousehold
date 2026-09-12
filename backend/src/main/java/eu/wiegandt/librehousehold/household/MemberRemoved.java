package eu.wiegandt.librehousehold.household;

import java.util.UUID;

/**
 * Domain event published when a member is permanently removed from a household.
 * Other modules listen to this event to clean up member-specific data.
 * Defined in the public package so consuming modules can reference the type.
 *
 * <p>The member's name/email and the household name are carried by the event itself,
 * since the member is already deleted from the database by the time an
 * {@code @ApplicationModuleListener} (which runs {@code AFTER_COMMIT}) reacts to it.
 *
 * @param memberId      the ID of the member who was removed
 * @param memberName    the name of the member who was removed
 * @param email         the email of the member who was removed
 * @param householdName the name of the household the member was removed from
 */
public record MemberRemoved(UUID memberId, String memberName, String email, String householdName) {}
