import { get, writable } from 'svelte/store';
import { type Category, type CategoryUpdate, ExpensesApi } from '../../generated-sources/openapi';
import { ResponseError } from '../../generated-sources/openapi/runtime';
import { addToast } from './toastStore';
import { Toast } from '$lib/toast';
import { createApiConfig } from '$lib/api';

const expensesApi = new ExpensesApi(createApiConfig());

export const categories = writable<Category[]>([]);

export async function loadCategories(householdId: string): Promise<void> {
	const result = await expensesApi.getCategories({ householdId });
	categories.set(result);
}

export async function addCategory(householdId: string, category: Category): Promise<Category> {
	const savedCategory = await expensesApi.createCategory({
		householdId: householdId,
		category: category
	});
	categories.update((all) => [savedCategory, ...all]);
	return savedCategory;
}

export async function updateCategory(
	householdId: string,
	categoryId: string,
	update: CategoryUpdate
): Promise<Category> {
	try {
		const updated = await expensesApi.updateCategory({
			householdId,
			categoryId,
			categoryUpdate: update
		});
		categories.update((all) => all.map((c) => (c.id === categoryId ? updated : c)));
		return updated;
	} catch (error) {
		addToast(new Toast('Fehler beim Speichern der Kategorie.', 'error', 5000));
		throw error;
	}
}

export async function deleteCategory(householdId: string, categoryId: string): Promise<void> {
	let previousCategories: Category[] = [];
	categories.update((all) => {
		previousCategories = [...all];
		return all.filter((c) => c.id !== categoryId);
	});
	try {
		await expensesApi.deleteCategory({ householdId, categoryId });
	} catch (error) {
		categories.set(previousCategories);
		if (error instanceof ResponseError && error.response.status === 409) {
			addToast(
				new Toast('Category cannot be deleted because it is still used by expenses.', 'error', 5000)
			);
		} else {
			addToast(new Toast('Failed to delete category.', 'error', 5000));
		}
	}
}

export async function findCategory(
	householdId: string,
	categoryId: string
): Promise<Category | undefined> {
	let found = get(categories).find((c) => c.id === categoryId);
	if (!found) {
		await loadCategories(householdId);
		found = get(categories).find((c) => c.id === categoryId);
	}
	return found;
}
