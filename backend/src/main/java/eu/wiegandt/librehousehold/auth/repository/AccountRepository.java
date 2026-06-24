package eu.wiegandt.librehousehold.auth.repository;

import eu.wiegandt.librehousehold.auth.model.AccountEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends CrudRepository<AccountEntity, UUID> {

    Optional<AccountEntity> findByEmail(String email);
}
