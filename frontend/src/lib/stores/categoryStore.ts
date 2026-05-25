import { get, writable } from 'svelte/store';
import { type Category, Configuration, HouseholdApi } from '../../generated-sources/openapi';

const apiConfig = new Configuration({ basePath: '/api' });
const householdApi = new HouseholdApi(apiConfig);

export const categories = writable<Category[]>([]);

export async function loadCategories(householdId: string): Promise<void> {
	const result = await householdApi.getCategories({ householdId });
	categories.set(result);
}

export async function addCategory(householdId: string, category: Category): Promise<Category> {
	const savedCategory = await householdApi.createCategory({
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
