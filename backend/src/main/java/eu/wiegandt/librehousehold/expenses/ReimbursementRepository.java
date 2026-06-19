package eu.wiegandt.librehousehold.expenses;

import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ReimbursementRepository extends CrudRepository<ReimbursementEntity, UUID> {

    List<ReimbursementEntity> findByHouseholdId(UUID householdId);

    Optional<ReimbursementEntity> findByIdAndHouseholdId(UUID id, UUID householdId);

    boolean existsByHouseholdIdAndCreditorIdAndStatusIn(UUID householdId, UUID creditorId, List<String> statuses);

    void deleteByHouseholdId(UUID householdId);
}
