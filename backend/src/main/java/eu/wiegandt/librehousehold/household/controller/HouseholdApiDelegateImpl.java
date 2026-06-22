package eu.wiegandt.librehousehold.household.controller;

import eu.wiegandt.librehousehold.api.HouseholdApiDelegate;
import eu.wiegandt.librehousehold.household.exception.HouseholdSetupIsRequiredException;
import eu.wiegandt.librehousehold.household.service.HouseholdManagementService;
import eu.wiegandt.librehousehold.household.service.HouseholdSetupService;
import eu.wiegandt.librehousehold.model.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class HouseholdApiDelegateImpl implements HouseholdApiDelegate {

    private final HouseholdSetupService householdSetupService;
    private final HouseholdManagementService householdManagementService;

    public HouseholdApiDelegateImpl(HouseholdSetupService householdSetupService,
                             HouseholdManagementService householdManagementService) {
        this.householdSetupService = householdSetupService;
        this.householdManagementService = householdManagementService;
    }

    @Override
    public ResponseEntity<HouseholdSetupResponse> setupHousehold(Optional<HouseholdSetup> householdSetup) {
        var setup = householdSetup.orElseThrow(HouseholdSetupIsRequiredException::new);
        return ResponseEntity.ok(householdSetupService.setupHousehold(setup));
    }

    @Override
    public ResponseEntity<Void> updateHousehold(UUID householdId, HouseholdUpdate householdUpdate) {
        householdManagementService.updateName(householdId, householdUpdate);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deleteHousehold(UUID householdId) {
        householdManagementService.deleteHousehold(householdId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<InviteResponse> getInvite(UUID householdId) {
        return ResponseEntity.ok(householdManagementService.getInvite(householdId));
    }

    @Override
    public ResponseEntity<InviteResponse> generateInviteLink(UUID householdId) {
        return ResponseEntity.ok(householdManagementService.regenerateInvite(householdId));
    }

    @Override
    public ResponseEntity<Void> transferOwnership(UUID householdId, TransferOwnershipRequest transferOwnershipRequest) {
        householdManagementService.transferOwnership(householdId, transferOwnershipRequest.getMemberId());
        return ResponseEntity.noContent().build();
    }
}
