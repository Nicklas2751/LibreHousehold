package eu.wiegandt.librehousehold.household;

import java.util.UUID;

/**
 * Named interface for cross-module synchronous queries about households.
 * Implemented by the household module; consumed by other modules that need
 * to verify household existence without depending on internal types.
 */
public interface HouseholdQuery {

    /**
     * Returns {@code true} if a household with the given ID exists.
     *
     * @param householdId the ID of the household to check
     * @return {@code true} if the household exists, {@code false} otherwise
     */
    boolean householdExists(UUID householdId);
}
