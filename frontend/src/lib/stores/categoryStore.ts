import { get, writable } from 'svelte/store';
import { type Category, Configuration, ExpensesApi } from '../../generated-sources/openapi';

const apiConfig = new Configuration({ basePath: '/api' });
const expensesApi = new ExpensesApi(apiConfig);

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
