package eu.wiegandt.librehousehold.household;

import java.util.UUID;

/**
 * Domain event published by {@code UnverifiedAccountExpiryJob} when an unverified account is about
 * to be deleted for grace-period expiry, so the member gets one last chance to verify their email.
 * Kept as a domain event (rather than a direct call into {@code notifications}) for the same reason
 * as {@link AccountRegistered}/{@link VerificationEmailRequested}: {@code household} must not depend
 * on {@code notifications} (see ADR-011) — a direct call would also create a module dependency
 * cycle, since {@code notifications} already depends on {@code household}'s public API.
 *
 * @param memberId the ID of the member whose account is about to be deleted
 * @param email    the email address to send the warning to
 */
public record VerificationDeletionWarningRequested(UUID memberId, String email) {}
