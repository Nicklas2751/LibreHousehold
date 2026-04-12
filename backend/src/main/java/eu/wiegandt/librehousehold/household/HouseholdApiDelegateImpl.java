package eu.wiegandt.librehousehold.household;

import eu.wiegandt.librehousehold.api.HouseholdApiDelegate;
import eu.wiegandt.librehousehold.model.Household;
import eu.wiegandt.librehousehold.model.HouseholdSetup;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class HouseholdApiDelegateImpl implements HouseholdApiDelegate {

    @Override
    public ResponseEntity<Household> setupHousehold(Optional<HouseholdSetup> householdSetup) {
        var setup = householdSetup.orElseThrow(() -> new HouseholdSetupIsRequiredException());
        return HouseholdApiDelegate.super.setupHousehold(householdSetup);
    }
}
