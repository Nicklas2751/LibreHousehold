package eu.wiegandt.librehousehold.household;

import java.util.UUID;

/**
 * Domain event published when a member explicitly requests that their email verification link be
 * resent (see the {@code /verification/resend} endpoint). Kept distinct from {@link AccountRegistered}
 * on purpose: reusing that event for a resend would misleadingly suggest the account was registered
 * again. Defined in the public package so consuming modules can reference the type.
 *
 * @param memberId the ID of the member requesting the resend
 * @param email    the email address to resend the verification link to
 */
public record VerificationEmailRequested(UUID memberId, String email) {}
