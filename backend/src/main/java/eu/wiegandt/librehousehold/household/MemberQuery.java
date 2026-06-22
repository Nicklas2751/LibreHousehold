package eu.wiegandt.librehousehold.household;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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
}
