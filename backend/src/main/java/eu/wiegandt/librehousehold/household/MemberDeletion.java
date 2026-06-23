package eu.wiegandt.librehousehold.household;

import java.util.UUID;

/**
 * Named interface allowing other modules to trigger self-service account removal.
 * The household module owns the removal logic, including publishing MemberRemoved.
 */
public interface MemberDeletion {

    void removeMember(UUID memberId);
}
