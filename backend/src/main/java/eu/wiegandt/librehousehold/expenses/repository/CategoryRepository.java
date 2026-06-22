package eu.wiegandt.librehousehold.expenses.repository;

import eu.wiegandt.librehousehold.expenses.model.CategoryEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends CrudRepository<CategoryEntity, UUID> {

    List<CategoryEntity> findByHouseholdId(UUID householdId);

    Optional<CategoryEntity> findByIdAndHouseholdId(UUID id, UUID householdId);

    boolean existsByHouseholdIdAndName(UUID householdId, String name);

    boolean existsByHouseholdIdAndNameAndIdNot(UUID householdId, String name, UUID id);

    void deleteByHouseholdId(UUID householdId);
}
