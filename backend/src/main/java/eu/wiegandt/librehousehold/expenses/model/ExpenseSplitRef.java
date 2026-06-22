package eu.wiegandt.librehousehold.expenses.model;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table(schema = "expenses", value = "expense_split")
public record ExpenseSplitRef(@Column("member_id") UUID memberId) {
}
