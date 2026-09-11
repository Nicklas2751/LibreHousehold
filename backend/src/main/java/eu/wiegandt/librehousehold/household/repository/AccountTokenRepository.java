package eu.wiegandt.librehousehold.household.repository;

import eu.wiegandt.librehousehold.household.model.AccountTokenEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountTokenRepository extends CrudRepository<AccountTokenEntity, Long> {

    Optional<AccountTokenEntity> findByTokenAndPurpose(UUID token, String purpose);

    @Modifying
    void deleteByMemberIdAndPurpose(UUID memberId, String purpose);
}
