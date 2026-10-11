package eu.wiegandt.librehousehold.household.repository;

import eu.wiegandt.librehousehold.household.model.AccountIdentityEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface AccountIdentityRepository extends CrudRepository<AccountIdentityEntity, Long> {

    Optional<AccountIdentityEntity> findByProviderIdAndExternalSubject(String providerId, String externalSubject);
}
