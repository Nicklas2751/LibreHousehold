package eu.wiegandt.librehousehold.household.repository;

import eu.wiegandt.librehousehold.household.model.InviteEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InviteRepository extends CrudRepository<InviteEntity, Long> {

    Optional<InviteEntity> findByHouseholdId(UUID householdId);

    Optional<InviteEntity> findByToken(UUID token);

    @Modifying
    @Query("DELETE FROM invite WHERE household_id = :householdId")
    void deleteByHouseholdId(@Param("householdId") UUID householdId);
}
