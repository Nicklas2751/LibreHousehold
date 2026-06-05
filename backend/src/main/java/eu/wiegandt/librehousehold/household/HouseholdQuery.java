package eu.wiegandt.librehousehold.household;

import java.util.UUID;

public interface HouseholdQuery {

    boolean householdExists(UUID householdId);
}
