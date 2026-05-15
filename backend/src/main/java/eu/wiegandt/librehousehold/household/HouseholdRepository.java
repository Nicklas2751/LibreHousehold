package eu.wiegandt.librehousehold.household;

import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

interface HouseholdRepository extends CrudRepository<HouseholdEntity, UUID> {}
