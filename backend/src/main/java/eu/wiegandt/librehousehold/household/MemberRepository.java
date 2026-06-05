package eu.wiegandt.librehousehold.household;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface MemberRepository extends CrudRepository<MemberEntity, UUID> {

    List<MemberEntity> findByHouseholdId(UUID householdId);

    Optional<MemberEntity> findByHouseholdIdAndIsAdminTrue(UUID householdId);

    void deleteByHouseholdId(UUID householdId);

    @Modifying
    @Query("UPDATE member SET name = :name WHERE id = :id")
    int updateName(@Param("id") UUID id, @Param("name") String name);

    @Modifying
    @Query("UPDATE member SET email = :email WHERE id = :id")
    int updateEmail(@Param("id") UUID id, @Param("email") String email);

    @Modifying
    @Query("UPDATE member SET is_admin = false WHERE household_id = :householdId AND is_admin = true")
    int revokeAdmin(@Param("householdId") UUID householdId);

    @Modifying
    @Query("UPDATE member SET is_admin = true WHERE id = :memberId")
    int grantAdmin(@Param("memberId") UUID memberId);

    @Query("SELECT id, name FROM member WHERE id IN (:ids)")
    List<MemberNameProjection> findNamesByIds(@Param("ids") Collection<UUID> ids);
}
