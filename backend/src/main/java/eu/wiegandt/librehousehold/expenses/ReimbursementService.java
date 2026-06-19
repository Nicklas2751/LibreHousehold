package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.model.Reimbursement;
import eu.wiegandt.librehousehold.model.ReimbursementCreate;
import eu.wiegandt.librehousehold.model.ReimbursementUpdate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
class ReimbursementService {

    private final ReimbursementRepository reimbursementRepository;
    private final ReimbursementMapper reimbursementMapper;

    ReimbursementService(ReimbursementRepository reimbursementRepository,
                         ReimbursementMapper reimbursementMapper) {
        this.reimbursementRepository = reimbursementRepository;
        this.reimbursementMapper = reimbursementMapper;
    }

    List<Reimbursement> getReimbursements(UUID householdId) {
        return reimbursementRepository.findByHouseholdId(householdId).stream()
                .map(reimbursementMapper::toReimbursement)
                .toList();
    }

    Reimbursement createReimbursement(UUID householdId, ReimbursementCreate create) {
        var entity = reimbursementMapper.toEntity(create, UUID.randomUUID(), householdId);
        var saved = reimbursementRepository.save(entity);
        return reimbursementMapper.toReimbursement(saved);
    }

    Reimbursement updateReimbursement(UUID householdId, UUID reimbursementId, ReimbursementUpdate update) {
        var entity = reimbursementRepository.findByIdAndHouseholdId(reimbursementId, householdId)
                .orElseThrow(ReimbursementNotFoundException::new);

        reimbursementMapper.updateEntityFromUpdate(update, entity);
        reimbursementRepository.save(entity);
        return reimbursementMapper.toReimbursement(entity);
    }
}
