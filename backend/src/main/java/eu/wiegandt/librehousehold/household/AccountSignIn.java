package eu.wiegandt.librehousehold.household;

import java.util.Optional;
import java.util.UUID;

/**
 * Signs in the member linked to an external identity right after the social-login callback (see
 * ADR-013, ADR-018). Deliberately a command that also answers a query: a credential-less sign-in by
 * member ID must not be callable from other modules, so the lookup and the sign-in cannot be split
 * into two methods of this interface.
 */
public interface AccountSignIn {

    /**
     * Establishes the member's session in the current request, if an account is linked to the
     * given external identity.
     *
     * @param providerId      the social-login provider: its issuer for OIDC, otherwise its user-info
     *                        URI without query and fragment (see ADR-018)
     * @param externalSubject the provider's stable identifier of the user (e.g. the OIDC {@code sub} claim)
     * @return the signed-in member's ID; empty, and no session change, if no account is linked
     */
    Optional<UUID> signInByExternalIdentity(String providerId, String externalSubject);
}
