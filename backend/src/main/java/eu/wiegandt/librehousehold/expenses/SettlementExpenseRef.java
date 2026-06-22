package eu.wiegandt.librehousehold.expenses;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table(schema = "expenses", value = "settlement_expense")
record SettlementExpenseRef(@Column("expense_id") UUID expenseId) {}
