package eu.wiegandt.librehousehold.household;

import org.jspecify.annotations.Nullable;

/**
 * A user's identity at an external social-login provider that is about to become a member
 * (household setup or invite join). Only created with an e-mail address the provider has verified.
 *
 * @param registrationId the client registration ID of the social-login provider
 * @param subject        the provider's stable identifier of the user (e.g. the OIDC {@code sub} claim)
 * @param email          the e-mail address the provider verified
 * @param name           the name the provider reported; {@code null} if it reported none
 */
public record ExternalIdentity(String registrationId, String subject, String email, @Nullable String name) {
}
