package eu.wiegandt.librehousehold.household;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface MemberQuery {

    Map<UUID, String> findMemberNamesByIds(Collection<UUID> memberIds);
}
