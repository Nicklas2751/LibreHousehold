package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.model.HouseholdSetupResponse;

import java.util.UUID;

public interface HouseholdSetupPort {

    HouseholdSetupResponse setupForLocalRegistration(
            UUID adminId,
            String householdName,
            String householdImage,
            String memberName,
            String memberAvatar
    );
}
