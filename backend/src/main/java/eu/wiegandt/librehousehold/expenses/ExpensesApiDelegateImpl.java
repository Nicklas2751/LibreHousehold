package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.api.ExpensesApiDelegate;
import eu.wiegandt.librehousehold.model.Category;
import eu.wiegandt.librehousehold.model.Expense;
import eu.wiegandt.librehousehold.model.ExpenseUpdate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ExpensesApiDelegateImpl implements ExpensesApiDelegate {

    private final CategoryService categoryService;
    private final ExpenseService expenseService;

    ExpensesApiDelegateImpl(CategoryService categoryService, ExpenseService expenseService) {
        this.categoryService = categoryService;
        this.expenseService = expenseService;
    }

    @Override
    public ResponseEntity<List<Category>> getCategories(UUID householdId) {
        return ResponseEntity.ok(categoryService.getCategories(householdId));
    }

    @Override
    public ResponseEntity<Category> createCategory(UUID householdId, Optional<Category> category) {
        var cat = category.orElseThrow(CategoryBodyIsRequiredException::new);
        return ResponseEntity.ok(categoryService.createCategory(householdId, cat));
    }

    @Override
    public ResponseEntity<List<Expense>> getExpenses(UUID householdId) {
        return ResponseEntity.ok(expenseService.getExpenses(householdId));
    }

    @Override
    public ResponseEntity<Expense> getExpense(UUID householdId, UUID expenseId) {
        return ResponseEntity.ok(expenseService.getExpense(householdId, expenseId));
    }

    @Override
    public ResponseEntity<Expense> createExpense(UUID householdId, Optional<Expense> expense) {
        var exp = expense.orElseThrow(ExpenseBodyIsRequiredException::new);
        return ResponseEntity.ok(expenseService.createExpense(householdId, exp));
    }

    @Override
    public ResponseEntity<Expense> updateExpense(UUID householdId, UUID expenseId, Optional<ExpenseUpdate> expenseUpdate) {
        var update = expenseUpdate.orElse(new ExpenseUpdate());
        return ResponseEntity.ok(expenseService.updateExpense(householdId, expenseId, update));
    }

    @Override
    public ResponseEntity<Void> deleteExpense(UUID householdId, UUID expenseId) {
        expenseService.deleteExpense(householdId, expenseId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<Expense>> getDebtorExpenses(UUID householdId, UUID payerId, UUID debtorId) {
        return ResponseEntity.ok(expenseService.getDebtorExpenses(householdId, payerId, debtorId));
    }
}
