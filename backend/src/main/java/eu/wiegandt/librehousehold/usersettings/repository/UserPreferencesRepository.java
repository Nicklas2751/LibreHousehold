package eu.wiegandt.librehousehold.usersettings.repository;

import eu.wiegandt.librehousehold.usersettings.model.UserPreferencesEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface UserPreferencesRepository extends CrudRepository<UserPreferencesEntity, UUID> {

    void deleteByMemberId(UUID memberId);
}
