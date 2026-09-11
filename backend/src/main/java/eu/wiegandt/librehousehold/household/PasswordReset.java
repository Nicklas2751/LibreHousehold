package eu.wiegandt.librehousehold.household;

import java.util.UUID;

/**
 * Named interface for the {@code session} module to trigger the password-reset flow without
 * depending on {@code household}'s internal service classes (see ADR-011). Both password-reset
 * endpoints share the OpenAPI {@code session} tag and therefore the single generated
 * {@code SessionApiDelegate}, implemented by {@code SessionApiDelegateImpl} in the {@code session}
 * module, even though the underlying account/member logic belongs to {@code household}. Implemented
 * by {@code PasswordResetService}.
 */
public interface PasswordReset {

    /**
     * Sends a password reset link to the given email address, if an account exists for it. Does
     * nothing otherwise (ENUM1 in Arc42 Chapter 8: callers must not learn whether the address
     * exists).
     */
    void requestPasswordReset(String email);

    /**
     * Sets a new password for the account identified by the given, single-use reset token, and
     * invalidates all of that account's existing sessions.
     *
     * @throws eu.wiegandt.librehousehold.household.exception.PasswordResetTokenInvalidException if
     *                                                                                            the token is unknown or expired
     */
    void confirmPasswordReset(UUID token, String newPassword);
}
