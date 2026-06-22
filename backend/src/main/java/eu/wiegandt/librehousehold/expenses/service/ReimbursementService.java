package eu.wiegandt.librehousehold.expenses.service;

import eu.wiegandt.librehousehold.expenses.exception.ReimbursementNotFoundException;
import eu.wiegandt.librehousehold.expenses.mapper.ReimbursementMapper;
import eu.wiegandt.librehousehold.expenses.model.SettlementExpenseRef;
import eu.wiegandt.librehousehold.expenses.repository.ExpenseRepository;
import eu.wiegandt.librehousehold.expenses.repository.ReimbursementRepository;
import eu.wiegandt.librehousehold.model.Reimbursement;
import eu.wiegandt.librehousehold.model.ReimbursementCreate;
import eu.wiegandt.librehousehold.model.ReimbursementUpdate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReimbursementService {

    private final ReimbursementRepository reimbursementRepository;
    private final ExpenseRepository expenseRepository;
    private final ReimbursementMapper reimbursementMapper;

    public ReimbursementService(ReimbursementRepository reimbursementRepository,
                         ExpenseRepository expenseRepository,
                         ReimbursementMapper reimbursementMapper) {
        this.reimbursementRepository = reimbursementRepository;
        this.expenseRepository = expenseRepository;
        this.reimbursementMapper = reimbursementMapper;
    }

    public List<Reimbursement> getReimbursements(UUID householdId) {
        return reimbursementRepository.findByHouseholdId(householdId).stream()
                .map(reimbursementMapper::toReimbursement)
                .toList();
    }

    public Reimbursement createReimbursement(UUID householdId, ReimbursementCreate create) {
        var entity = reimbursementMapper.toEntity(create, UUID.randomUUID(), householdId);
        var coveredExpenses = expenseRepository
                .findDebtorExpenses(householdId, create.getCreditorId(), create.getDebtorId())
                .stream()
                .map(e -> new SettlementExpenseRef(e.getId()))
                .collect(Collectors.toSet());
        entity.setCoveredExpenses(coveredExpenses);
        var saved = reimbursementRepository.save(entity);
        return reimbursementMapper.toReimbursement(saved);
    }

    public Reimbursement updateReimbursement(UUID householdId, UUID reimbursementId, ReimbursementUpdate update) {
        var entity = reimbursementRepository.findByIdAndHouseholdId(reimbursementId, householdId)
                .orElseThrow(ReimbursementNotFoundException::new);

        reimbursementMapper.updateEntityFromUpdate(update, entity);
        entity.markExisting();
        reimbursementRepository.save(entity);
        return reimbursementMapper.toReimbursement(entity);
    }
}
