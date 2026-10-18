package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.model.Member;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Named interface for cross-module synchronous queries about household members.
 * Implemented by the household module; consumed by other modules that need
 * member information without depending on internal types.
 */
public interface MemberQuery {

    /**
     * Returns a map of member IDs to display names for the given set of member IDs.
     * Members not found are simply absent from the result map.
     *
     * @param memberIds the IDs of the members to look up
     * @return a map from member ID to display name
     */
    Map<UUID, String> findMemberNamesByIds(Collection<UUID> memberIds);

    /**
     * Returns all member IDs that belong to the given household.
     *
     * @param householdId the ID of the household
     * @return the member IDs; empty if the household has no members
     */
    List<UUID> findMemberIdsByHouseholdId(UUID householdId);

    /**
     * Returns true if a member with the given ID exists.
     */
    boolean memberExistsById(UUID memberId);

    /**
     * Returns true if the member with the given ID is the household admin.
     */
    boolean isAdmin(UUID memberId);

    /**
     * Returns the member with the given ID.
     *
     * @param memberId the ID of the member
     * @return the member
     * @throws eu.wiegandt.librehousehold.household.exception.MemberNotFoundException if no member with this ID exists
     */
    Member getMember(UUID memberId);

    /**
     * Returns {@code true} if the account belonging to the given member has a verified email address.
     *
     * @param memberId the ID of the member
     * @return {@code true} if the member's account email is verified
     */
    boolean isEmailVerified(UUID memberId);

    /**
     * Returns {@code true} if the account belonging to the given member signs in only via a social-login
     * provider, because it was registered there and has no local password.
     *
     * @param memberId the ID of the member
     * @return {@code true} if the member's account has no local password
     */
    boolean isSocialLoginOnly(UUID memberId);

    /**
     * Returns the e-mail address of the member whose account is linked to the given external identity.
     *
     * @param providerId      the client registration ID of the social-login provider
     * @param externalSubject the provider's stable identifier of the user (e.g. the OIDC {@code sub} claim)
     * @return the member's e-mail address; empty if no account is linked to this identity
     */
    Optional<String> findEmailByExternalIdentity(String providerId, String externalSubject);
}
