import type {Expense} from "../generated-sources/openapi";

/**
 * Checks if an expense is mutable (can be edited or deleted).
 *
 * Currently (V1), an expense is mutable if:
 * 1. The user has the necessary permissions (checked elsewhere via 'paidBy').
 * 2. No settlements exist that cover this expense (Not yet implemented, so currently always true regarding this rule).
 *
 * Future V2 Implementation:
 * Check if there are any confirmed settlements involving the payer and the split members
 * that occurred after this expense.
 *
 * @param expense The expense to check (not used currently but required for future logic)
 * @returns true if the expense can be modified, false otherwise.
 */
export function isExpenseMutable(expense: Expense): boolean {
    // TODO: T3 - Implement Settlement check logic here based on T2 debt calculation results
    return true;
}


