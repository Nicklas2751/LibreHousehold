import {writable} from 'svelte/store';
import {Configuration, type Expense, ExpensesApi, type ExpenseUpdate} from '../../generated-sources/openapi';
import {addToast} from "$lib/stores/toastStore";
import {Toast} from "$lib/toast";

const apiConfig = new Configuration({basePath: '/api'});
const api = new ExpensesApi(apiConfig);

export const expenses = writable<Expense[]>([]);

export async function loadExpenses(householdId: string): Promise<void> {
    const result = await api.getExpenses({householdId});
    expenses.set(result);
}

export async function addExpense(householdId: string, expense: Expense): Promise<Expense> {
    let savedExpense = await api.createExpense({householdId: householdId, expense: expense});
    expenses.update((all) => [savedExpense, ...all]);
    return savedExpense;
}

export async function updateExpense(
    householdId: string,
    expenseId: string,
    expenseUpdate: ExpenseUpdate
): Promise<void> {
    // Optimistic update: Update the store immediately
    let previousExpenses: Expense[] = [];
    expenses.update((all) => {
        previousExpenses = [...all];
        return all.map((expense) =>
            expense.id === expenseId ? {...expense, ...expenseUpdate} : expense
        );
    });

    try {
        await api.updateExpense({householdId, expenseId, expenseUpdate});
    } catch (error) {
        // Rollback on error
        expenses.set(previousExpenses);
        addToast(new Toast("Failed to update expense", "error", 5000));
        console.error("Failed to update expense:", error);
    }
}

export async function deleteExpense(householdId: string, expenseId: string): Promise<void> {
    await api.deleteExpense({householdId, expenseId});
    await loadExpenses(householdId);

    // Optimistic update: Update the store immediately
    let previousExpenses: Expense[] = [];
    expenses.update((all) => {
        previousExpenses = [...all];
        return all.filter((expense) => expense.id !== expenseId);
    });

    try {
        await api.deleteExpense({householdId, expenseId});
    } catch (error) {
        // Rollback on error
        expenses.set(previousExpenses);
        addToast(new Toast("Failed to delete expense", "error", 5000));
        console.error("Failed to delete expense:", error);
    }
}

