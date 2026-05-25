package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.api.HouseholdApiDelegate;
import eu.wiegandt.librehousehold.model.HouseholdSetup;
import eu.wiegandt.librehousehold.model.HouseholdSetupResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class HouseholdApiDelegateImpl implements HouseholdApiDelegate {

    private final HouseholdSetupService householdSetupService;

    HouseholdApiDelegateImpl(HouseholdSetupService householdSetupService) {
        this.householdSetupService = householdSetupService;
    }

    @Override
    public ResponseEntity<HouseholdSetupResponse> setupHousehold(Optional<HouseholdSetup> householdSetup) {
        var setup = householdSetup.orElseThrow(HouseholdSetupIsRequiredException::new);
        return ResponseEntity.ok(householdSetupService.setupHousehold(setup));
    }
}
