package eu.wiegandt.librehousehold.household.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Links a member to an account at an external social-login provider. {@code providerId} is the
 * provider's issuer for OIDC, otherwise its user-info URI without query and fragment — never the
 * client registration ID, which a self-hoster may rename (see ADR-018). {@code externalSubject} is
 * the provider's stable user identifier (e.g. the OIDC {@code sub} claim) — never the e-mail
 * address, which the provider may let users change.
 */
@Table("account_identity")
public record AccountIdentityEntity(
        @Id Long id,
        @Column("member_id") UUID memberId,
        @Column("provider_id") String providerId,
        @Column("external_subject") String externalSubject,
        @Column("linked_at") Instant linkedAt
) {
}
