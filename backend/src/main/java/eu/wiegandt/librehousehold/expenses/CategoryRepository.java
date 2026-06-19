package eu.wiegandt.librehousehold.expenses;

import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

interface CategoryRepository extends CrudRepository<CategoryEntity, UUID> {

    List<CategoryEntity> findByHouseholdId(UUID householdId);

    boolean existsByHouseholdIdAndName(UUID householdId, String name);

    void deleteByHouseholdId(UUID householdId);
}
