package eu.wiegandt.librehousehold.household.controller;

import eu.wiegandt.librehousehold.api.HouseholdApiDelegate;
import eu.wiegandt.librehousehold.household.exception.HouseholdSetupIsRequiredException;
import eu.wiegandt.librehousehold.household.service.AccountSessionSignIn;
import eu.wiegandt.librehousehold.household.service.HouseholdManagementService;
import eu.wiegandt.librehousehold.household.service.HouseholdSetupService;
import eu.wiegandt.librehousehold.model.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class HouseholdApiDelegateImpl implements HouseholdApiDelegate {

    private final HouseholdSetupService householdSetupService;
    private final HouseholdManagementService householdManagementService;
    private final AccountSessionSignIn accountSessionSignIn;

    public HouseholdApiDelegateImpl(HouseholdSetupService householdSetupService,
                                    HouseholdManagementService householdManagementService,
                                    AccountSessionSignIn accountSessionSignIn) {
        this.householdSetupService = householdSetupService;
        this.householdManagementService = householdManagementService;
        this.accountSessionSignIn = accountSessionSignIn;
    }

    // Signs in only after the setup transaction committed, so a rollback never leaves a session
    // for a member that does not exist
    @Override
    public ResponseEntity<HouseholdSetupResponse> setupHousehold(Optional<HouseholdSetup> householdSetup) {
        var setup = householdSetup.orElseThrow(HouseholdSetupIsRequiredException::new);
        var response = householdSetupService.setupHousehold(setup);
        accountSessionSignIn.signIn(setup.getMember().getId());
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("@householdAccessGuard.isAdminOfHousehold(#householdId, authentication)")
    public ResponseEntity<Void> updateHousehold(UUID householdId, HouseholdUpdate householdUpdate) {
        householdManagementService.updateName(householdId, householdUpdate);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("@householdAccessGuard.isAdminOfHousehold(#householdId, authentication)")
    public ResponseEntity<Void> deleteHousehold(UUID householdId) {
        householdManagementService.deleteHousehold(householdId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("@householdAccessGuard.isAdminOfHousehold(#householdId, authentication)")
    public ResponseEntity<InviteResponse> getInvite(UUID householdId) {
        return ResponseEntity.ok(householdManagementService.getInvite(householdId));
    }

    @Override
    @PreAuthorize("@householdAccessGuard.isAdminOfHousehold(#householdId, authentication)")
    public ResponseEntity<InviteResponse> generateInviteLink(UUID householdId) {
        return ResponseEntity.ok(householdManagementService.regenerateInvite(householdId));
    }

    @Override
    @PreAuthorize("@householdAccessGuard.isAdminOfHousehold(#householdId, authentication)")
    public ResponseEntity<Void> transferOwnership(UUID householdId, TransferOwnershipRequest transferOwnershipRequest) {
        householdManagementService.transferOwnership(householdId, transferOwnershipRequest.getMemberId());
        return ResponseEntity.noContent().build();
    }
}
