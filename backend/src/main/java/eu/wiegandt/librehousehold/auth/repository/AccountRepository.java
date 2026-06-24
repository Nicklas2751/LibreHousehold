package eu.wiegandt.librehousehold.auth.repository;

import eu.wiegandt.librehousehold.auth.model.AccountEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends CrudRepository<AccountEntity, UUID> {

    Optional<AccountEntity> findByEmail(String email);

    @Transactional
    @Modifying
    @Query("UPDATE auth.account SET email = :email WHERE id = :id")
    void updateEmail(@Param("id") UUID id, @Param("email") String email);
}
