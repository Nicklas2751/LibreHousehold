package eu.wiegandt.librehousehold.household.repository;

import eu.wiegandt.librehousehold.household.model.AccountEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AccountRepository extends CrudRepository<AccountEntity, UUID> {

    @Modifying
    @Query("UPDATE account SET password_hash = :passwordHash WHERE member_id = :memberId")
    void updatePasswordHash(@Param("memberId") UUID memberId, @Param("passwordHash") String passwordHash);
}
