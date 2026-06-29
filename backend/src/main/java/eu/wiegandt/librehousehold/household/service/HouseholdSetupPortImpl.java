package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.HouseholdSetupPort;
import eu.wiegandt.librehousehold.model.HouseholdSetupResponse;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class HouseholdSetupPortImpl implements HouseholdSetupPort {

    private final HouseholdSetupService householdSetupService;

    HouseholdSetupPortImpl(HouseholdSetupService householdSetupService) {
        this.householdSetupService = householdSetupService;
    }

    @Override
    public HouseholdSetupResponse setupForLocalRegistration(UUID adminId, String householdName,
                                                             String householdImage, String memberName,
                                                             String memberAvatar) {
        return householdSetupService.setupHousehold(adminId, householdName, householdImage, memberName, memberAvatar);
    }
}
