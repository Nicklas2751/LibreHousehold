package eu.wiegandt.librehousehold.usersettings.controller;

import eu.wiegandt.librehousehold.api.UsersettingsApiDelegate;
import eu.wiegandt.librehousehold.auth.OnlyAuthor;
import eu.wiegandt.librehousehold.model.UserPreferences;
import eu.wiegandt.librehousehold.usersettings.service.UsersettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UsersettingsApiDelegateImpl implements UsersettingsApiDelegate {

    private final UsersettingsService service;

    public UsersettingsApiDelegateImpl(UsersettingsService service) {
        this.service = service;
    }

    @Override
    @OnlyAuthor
    public ResponseEntity<UserPreferences> updatePreferences(UUID householdId,
                                                             UUID resourceId,
                                                             UserPreferences userPreferences) {
        return ResponseEntity.ok(service.updatePreferences(resourceId, userPreferences));
    }

    @Override
    @OnlyAuthor
    public ResponseEntity<Void> deleteAccount(UUID householdId, UUID resourceId) {
        service.deleteAccount(resourceId);
        return ResponseEntity.noContent().build();
    }
}
