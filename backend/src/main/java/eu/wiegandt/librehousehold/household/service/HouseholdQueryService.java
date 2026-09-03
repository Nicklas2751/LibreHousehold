package eu.wiegandt.librehousehold.household.service;

import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.household.exception.HouseholdNotFoundException;
import eu.wiegandt.librehousehold.household.mapper.HouseholdSetupMapper;
import eu.wiegandt.librehousehold.household.repository.HouseholdRepository;
import eu.wiegandt.librehousehold.model.Household;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class HouseholdQueryService implements HouseholdQuery {

    private final HouseholdRepository householdRepository;
    private final HouseholdSetupMapper householdMapper;

    public HouseholdQueryService(HouseholdRepository householdRepository, HouseholdSetupMapper householdMapper) {
        this.householdRepository = householdRepository;
        this.householdMapper = householdMapper;
    }

    @Override
    public boolean householdExists(UUID householdId) {
        return householdRepository.existsById(householdId);
    }

    @Override
    public Household getHousehold(UUID householdId) {
        return householdRepository.findById(householdId)
                .map(householdMapper::toApiModel)
                .orElseThrow(HouseholdNotFoundException::new);
    }
}
