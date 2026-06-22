package eu.wiegandt.librehousehold.expenses.service;

import eu.wiegandt.librehousehold.expenses.exception.ExpenseNotFoundException;
import eu.wiegandt.librehousehold.expenses.exception.ExpenseNotMutableException;
import eu.wiegandt.librehousehold.expenses.exception.HouseholdNotFoundException;
import eu.wiegandt.librehousehold.expenses.mapper.ExpenseMapper;
import eu.wiegandt.librehousehold.expenses.model.ExpenseSplitRef;
import eu.wiegandt.librehousehold.expenses.repository.CategoryRepository;
import eu.wiegandt.librehousehold.expenses.repository.ExpenseRepository;
import eu.wiegandt.librehousehold.expenses.repository.ReimbursementRepository;
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
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ReimbursementRepository reimbursementRepository;
    private final CategoryRepository categoryRepository;
    private final ExpenseMapper expenseMapper;
    private final HouseholdQuery householdQuery;
    private final MemberQuery memberQuery;

    public ExpenseService(ExpenseRepository expenseRepository,
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

    public List<Expense> getExpenses(UUID householdId) {
        return expenseRepository.findByHouseholdIdOrderByDateDesc(householdId).stream()
                .map(entity -> expenseMapper.toExpense(entity, isMutable(householdId, entity.getId())))
                .toList();
    }

    public Expense createExpense(UUID householdId, Expense expense) {
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
        return expenseMapper.toExpense(saved, isMutable(householdId, saved.getId()));
    }

    public Expense updateExpense(UUID householdId, UUID expenseId, ExpenseUpdate update) {
        var entity = expenseRepository.findByIdAndHouseholdId(expenseId, householdId)
                .orElseThrow(ExpenseNotFoundException::new);

        if (!isMutable(householdId, entity.getId())) {
            throw new ExpenseNotMutableException();
        }

        expenseMapper.updateEntityFromUpdate(update, entity);
        expenseRepository.save(entity);
        return expenseMapper.toExpense(entity, true);
    }

    public void deleteExpense(UUID householdId, UUID expenseId) {
        var entity = expenseRepository.findByIdAndHouseholdId(expenseId, householdId)
                .orElseThrow(ExpenseNotFoundException::new);

        if (!isMutable(householdId, entity.getId())) {
            throw new ExpenseNotMutableException();
        }

        expenseRepository.deleteById(expenseId);
    }

    public Expense getExpense(UUID householdId, UUID expenseId) {
        var entity = expenseRepository.findByIdAndHouseholdId(expenseId, householdId)
                .orElseThrow(ExpenseNotFoundException::new);
        return expenseMapper.toExpense(entity, isMutable(householdId, entity.getId()));
    }

    public List<Expense> getDebtorExpenses(UUID householdId, UUID payerId, UUID debtorId) {
        return expenseRepository.findDebtorExpenses(householdId, payerId, debtorId).stream()
                .map(entity -> expenseMapper.toExpense(entity, isMutable(householdId, entity.getId())))
                .toList();
    }

    @ApplicationModuleListener
    void onHouseholdDeleted(HouseholdDeleted event) {
        var householdId = event.householdId();
        reimbursementRepository.deleteByHouseholdId(householdId);
        expenseRepository.deleteAll(expenseRepository.findByHouseholdId(householdId));
        categoryRepository.deleteByHouseholdId(householdId);
    }

    private boolean isMutable(UUID householdId, UUID expenseId) {
        return !reimbursementRepository.existsActiveSettlementCoveringExpense(householdId, expenseId);
    }
}
