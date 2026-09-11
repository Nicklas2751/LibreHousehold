package eu.wiegandt.librehousehold.household;

import java.util.UUID;

/**
 * Domain event published when a member with a known email address requests a password reset (see
 * the {@code /password-reset/request} endpoint). Not published for unknown email addresses (ENUM1
 * in Arc42 Chapter 8: the endpoint must not leak whether an address is registered). Defined in the
 * public package so consuming modules can reference the type.
 *
 * @param memberId the ID of the member requesting the password reset
 * @param email    the email address to send the password reset link to
 */
public record PasswordResetRequested(UUID memberId, String email) {}
