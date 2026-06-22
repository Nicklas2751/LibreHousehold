package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class HouseholdQueryService implements HouseholdQuery {

    private final HouseholdRepository householdRepository;

    public HouseholdQueryService(HouseholdRepository householdRepository) {
        this.householdRepository = householdRepository;
    }

    @Override
    public boolean householdExists(UUID householdId) {
        return householdRepository.existsById(householdId);
    }
}
