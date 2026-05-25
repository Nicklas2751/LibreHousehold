package eu.wiegandt.librehousehold.household;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface InviteRepository extends CrudRepository<InviteEntity, Long> {

    Optional<InviteEntity> findByHouseholdId(UUID householdId);

    @Modifying
    @Query("DELETE FROM invite WHERE household_id = :householdId")
    void deleteByHouseholdId(@Param("householdId") UUID householdId);
}
