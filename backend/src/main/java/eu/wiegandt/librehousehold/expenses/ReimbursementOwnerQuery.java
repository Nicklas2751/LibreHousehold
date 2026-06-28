package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.core.ResourceOwnerQuery;
import eu.wiegandt.librehousehold.expenses.repository.ReimbursementRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class ReimbursementOwnerQuery implements ResourceOwnerQuery {

    private final ReimbursementRepository reimbursementRepository;

    ReimbursementOwnerQuery(ReimbursementRepository reimbursementRepository) {
        this.reimbursementRepository = reimbursementRepository;
    }

    @Override
    public boolean isOwner(UUID resourceId, UUID accountId) {
        return reimbursementRepository.findById(resourceId)
                .map(reimbursement -> accountId.equals(reimbursement.getCreditorId()))
                .orElse(false);
    }
}
