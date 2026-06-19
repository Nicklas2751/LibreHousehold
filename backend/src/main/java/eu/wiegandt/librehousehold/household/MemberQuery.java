package eu.wiegandt.librehousehold.household;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface MemberQuery {

    Map<UUID, String> findMemberNamesByIds(Collection<UUID> memberIds);

    List<UUID> findMemberIdsByHouseholdId(UUID householdId);
}
