package eu.wiegandt.librehousehold.household;

import java.util.UUID;

/**
 * Named interface for cross-module issuance of email verification tokens. Implemented by the
 * household module ({@code AccountTokenService}); consumed by the {@code notifications} module's
 * event listeners, which need to mint a token when reacting to an {@link AccountRegistered} or
 * {@link VerificationEmailRequested} event without depending on household's internal service
 * classes (see ADR-011).
 */
public interface AccountTokenIssuer {

    /**
     * Issues a new, single-use email verification token for the given member, replacing any
     * previously issued, still-valid verification token for that member.
     *
     * @param memberId the ID of the member to issue the token for
     * @return the newly issued token
     */
    UUID issueEmailVerificationToken(UUID memberId);
}
