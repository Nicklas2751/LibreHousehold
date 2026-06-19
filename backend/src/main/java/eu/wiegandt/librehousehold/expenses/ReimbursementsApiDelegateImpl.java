package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.api.ReimbursementsApiDelegate;
import eu.wiegandt.librehousehold.model.Reimbursement;
import eu.wiegandt.librehousehold.model.ReimbursementCreate;
import eu.wiegandt.librehousehold.model.ReimbursementUpdate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ReimbursementsApiDelegateImpl implements ReimbursementsApiDelegate {

    private final ReimbursementService reimbursementService;

    ReimbursementsApiDelegateImpl(ReimbursementService reimbursementService) {
        this.reimbursementService = reimbursementService;
    }

    @Override
    public ResponseEntity<List<Reimbursement>> getReimbursements(UUID householdId) {
        return ResponseEntity.ok(reimbursementService.getReimbursements(householdId));
    }

    @Override
    public ResponseEntity<Reimbursement> createReimbursement(UUID householdId, Optional<ReimbursementCreate> reimbursementCreate) {
        var create = reimbursementCreate.orElse(new ReimbursementCreate());
        return ResponseEntity.status(201).body(reimbursementService.createReimbursement(householdId, create));
    }

    @Override
    public ResponseEntity<Reimbursement> updateReimbursement(UUID householdId, UUID reimbursementId,
                                                              Optional<ReimbursementUpdate> reimbursementUpdate) {
        var update = reimbursementUpdate.orElse(new ReimbursementUpdate());
        return ResponseEntity.ok(reimbursementService.updateReimbursement(householdId, reimbursementId, update));
    }
}
