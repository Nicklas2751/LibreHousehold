package eu.wiegandt.librehousehold.household;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
class HouseholdQueryService implements HouseholdQuery {

    private final HouseholdRepository householdRepository;

    HouseholdQueryService(HouseholdRepository householdRepository) {
        this.householdRepository = householdRepository;
    }

    @Override
    public boolean householdExists(UUID householdId) {
        return householdRepository.existsById(householdId);
    }
}
