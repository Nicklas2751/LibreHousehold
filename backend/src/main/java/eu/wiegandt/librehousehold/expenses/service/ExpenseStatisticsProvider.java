package eu.wiegandt.librehousehold.expenses.service;

import eu.wiegandt.librehousehold.model.ExpenseStatsByCategory;
import eu.wiegandt.librehousehold.model.ExpenseStatsByMember;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExpenseStatisticsProvider {

    List<ExpenseStatsByCategory> getExpenseStatsByCategory(UUID householdId, LocalDate from, LocalDate to);

    List<ExpenseStatsByMember> getExpenseStatsByMember(UUID householdId, LocalDate from, LocalDate to);

    BigDecimal getTotalExpenses(UUID householdId, LocalDate from, LocalDate to);

    BigDecimal getAvgExpensesPerMonth(UUID householdId, LocalDate from, LocalDate to);
}
