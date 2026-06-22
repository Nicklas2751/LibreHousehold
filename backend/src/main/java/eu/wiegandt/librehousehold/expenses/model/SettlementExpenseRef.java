package eu.wiegandt.librehousehold.expenses.model;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table(schema = "expenses", value = "settlement_expense")
public record SettlementExpenseRef(@Column("expense_id") UUID expenseId) {}
