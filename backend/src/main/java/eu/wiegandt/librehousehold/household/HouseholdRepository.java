package eu.wiegandt.librehousehold.household;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface HouseholdRepository extends CrudRepository<HouseholdEntity, UUID> {

    @Query("SELECT name FROM household WHERE id = :id")
    Optional<String> findNameById(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE household SET name = :name WHERE id = :id")
    int updateName(@Param("id") UUID id, @Param("name") String name);

    @Modifying
    @Query("DELETE FROM household WHERE id = :id")
    int deleteHouseholdById(@Param("id") UUID id);
}
