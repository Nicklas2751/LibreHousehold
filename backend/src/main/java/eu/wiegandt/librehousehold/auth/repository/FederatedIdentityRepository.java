package eu.wiegandt.librehousehold.auth.repository;

import eu.wiegandt.librehousehold.auth.model.FederatedIdentityEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface FederatedIdentityRepository extends CrudRepository<FederatedIdentityEntity, UUID> {

    Optional<FederatedIdentityEntity> findByProviderAndProviderSub(String provider, String providerSub);
}
