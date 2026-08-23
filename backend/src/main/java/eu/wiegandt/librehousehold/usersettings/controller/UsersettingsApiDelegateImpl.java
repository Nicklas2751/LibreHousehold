package eu.wiegandt.librehousehold.usersettings.controller;

import eu.wiegandt.librehousehold.api.UsersettingsApiDelegate;
import eu.wiegandt.librehousehold.model.UserPreferences;
import eu.wiegandt.librehousehold.usersettings.service.UsersettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UsersettingsApiDelegateImpl implements UsersettingsApiDelegate {

    private final UsersettingsService service;

    public UsersettingsApiDelegateImpl(UsersettingsService service) {
        this.service = service;
    }

    @Override
    @PreAuthorize("@householdAccessGuard.isSelf(#memberId, authentication)")
    public ResponseEntity<UserPreferences> updatePreferences(UUID householdId,
                                                             UUID memberId,
                                                             UserPreferences userPreferences) {
        return ResponseEntity.ok(service.updatePreferences(memberId, userPreferences));
    }
}
