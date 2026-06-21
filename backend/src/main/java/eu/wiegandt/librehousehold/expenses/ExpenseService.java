package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.household.HouseholdDeleted;
import eu.wiegandt.librehousehold.household.HouseholdQuery;
import eu.wiegandt.librehousehold.household.MemberQuery;
import eu.wiegandt.librehousehold.model.Expense;
import eu.wiegandt.librehousehold.model.ExpenseUpdate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
class ExpenseService {

    private static final List<String> ACTIVE_REIMBURSEMENT_STATUSES = List.of("PENDING", "CONFIRMED");

    private final ExpenseRepository expenseRepository;
    private final ReimbursementRepository reimbursementRepository;
    private final CategoryRepository categoryRepository;
    private final ExpenseMapper expenseMapper;
    private final HouseholdQuery householdQuery;
    private final MemberQuery memberQuery;

    ExpenseService(ExpenseRepository expenseRepository,
                   ReimbursementRepository reimbursementRepository,
                   CategoryRepository categoryRepository,
                   ExpenseMapper expenseMapper,
                   HouseholdQuery householdQuery,
                   MemberQuery memberQuery) {
        this.expenseRepository = expenseRepository;
        this.reimbursementRepository = reimbursementRepository;
        this.categoryRepository = categoryRepository;
        this.expenseMapper = expenseMapper;
        this.householdQuery = householdQuery;
        this.memberQuery = memberQuery;
    }

    List<Expense> getExpenses(UUID householdId) {
        return expenseRepository.findByHouseholdIdOrderByDateDesc(householdId).stream()
                .map(entity -> expenseMapper.toExpense(entity, isMutable(householdId, entity.getPaidBy(), entity.getId())))
                .toList();
    }

    Expense createExpense(UUID householdId, Expense expense) {
        if (!householdQuery.householdExists(householdId)) {
            throw new HouseholdNotFoundException();
        }
        var entity = expenseMapper.toEntity(expense, householdId);
        if (entity.getSplitBetween().isEmpty()) {
            entity.setSplitBetween(memberQuery.findMemberIdsByHouseholdId(householdId).stream()
                    .map(ExpenseSplitRef::new)
                    .collect(Collectors.toSet()));
        }
        var saved = expenseRepository.save(entity);
        return expenseMapper.toExpense(saved, isMutable(householdId, saved.getPaidBy(), saved.getId()));
    }

    Expense updateExpense(UUID householdId, UUID expenseId, ExpenseUpdate update) {
        var entity = expenseRepository.findByIdAndHouseholdId(expenseId, householdId)
                .orElseThrow(ExpenseNotFoundException::new);

        if (!isMutable(householdId, entity.getPaidBy(), entity.getId())) {
            throw new ExpenseNotMutableException();
        }

        expenseMapper.updateEntityFromUpdate(update, entity);
        expenseRepository.save(entity);
        return expenseMapper.toExpense(entity, true);
    }

    void deleteExpense(UUID householdId, UUID expenseId) {
        var entity = expenseRepository.findByIdAndHouseholdId(expenseId, householdId)
                .orElseThrow(ExpenseNotFoundException::new);

        if (!isMutable(householdId, entity.getPaidBy(), entity.getId())) {
            throw new ExpenseNotMutableException();
        }

        expenseRepository.deleteById(expenseId);
    }

    Expense getExpense(UUID householdId, UUID expenseId) {
        var entity = expenseRepository.findByIdAndHouseholdId(expenseId, householdId)
                .orElseThrow(ExpenseNotFoundException::new);
        return expenseMapper.toExpense(entity, isMutable(householdId, entity.getPaidBy(), entity.getId()));
    }

    List<Expense> getDebtorExpenses(UUID householdId, UUID payerId, UUID debtorId) {
        return expenseRepository.findDebtorExpenses(householdId, payerId, debtorId).stream()
                .map(entity -> expenseMapper.toExpense(entity, isMutable(householdId, entity.getPaidBy(), entity.getId())))
                .toList();
    }

    @ApplicationModuleListener
    void onHouseholdDeleted(HouseholdDeleted event) {
        var householdId = event.householdId();
        reimbursementRepository.deleteByHouseholdId(householdId);
        expenseRepository.deleteAll(expenseRepository.findByHouseholdId(householdId));
        categoryRepository.deleteByHouseholdId(householdId);
    }

    private boolean isMutable(UUID householdId, UUID paidBy, UUID expenseId) {
        if (reimbursementRepository.existsByHouseholdIdAndCreditorIdAndStatusIn(
                householdId, paidBy, ACTIVE_REIMBURSEMENT_STATUSES)) {
            return false;
        }
        return !reimbursementRepository.existsActiveSettlementAsDebtorCoveringExpense(
                householdId, paidBy, expenseId);
    }
}
