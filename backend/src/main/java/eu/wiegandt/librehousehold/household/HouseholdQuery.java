package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.model.Household;

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

    /**
     * Returns the household with the given ID.
     *
     * @param householdId the ID of the household
     * @return the household
     * @throws eu.wiegandt.librehousehold.household.exception.HouseholdNotFoundException if no household with this ID exists
     */
    Household getHousehold(UUID householdId);
}
