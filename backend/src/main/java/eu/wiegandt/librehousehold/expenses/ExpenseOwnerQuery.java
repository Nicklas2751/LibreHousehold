package eu.wiegandt.librehousehold.expenses;

import eu.wiegandt.librehousehold.auth.ResourceOwnerQuery;
import eu.wiegandt.librehousehold.expenses.repository.ExpenseRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class ExpenseOwnerQuery implements ResourceOwnerQuery {

    private final ExpenseRepository expenseRepository;

    ExpenseOwnerQuery(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    @Override
    public boolean isOwner(UUID resourceId, UUID accountId) {
        return expenseRepository.findById(resourceId)
                .map(expense -> accountId.equals(expense.getPaidBy()))
                .orElse(false);
    }
}
