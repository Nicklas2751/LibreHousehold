package eu.wiegandt.librehousehold.household;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface HouseholdRepository extends CrudRepository<HouseholdEntity, UUID> {

    @Modifying
    @Query("UPDATE household SET name = :name WHERE id = :id")
    int updateName(@Param("id") UUID id, @Param("name") String name);

    @Modifying
    @Query("UPDATE household SET admin_id = :adminId WHERE id = :id")
    int updateAdminId(@Param("id") UUID id, @Param("adminId") UUID adminId);

    @Modifying
    @Query("DELETE FROM household WHERE id = :id")
    int deleteHouseholdById(@Param("id") UUID id);
}
