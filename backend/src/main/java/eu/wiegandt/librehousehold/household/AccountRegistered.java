package eu.wiegandt.librehousehold.household;

import java.util.UUID;

/**
 * Domain event published when a new account is registered (household setup or invite join).
 * Other modules listen to this event to react to new registrations, e.g. to send a verification
 * email. Defined in the public package so consuming modules can reference the type.
 *
 * @param memberId the ID of the member the new account belongs to
 * @param email    the email address of the new account
 */
public record AccountRegistered(UUID memberId, String email) {}
