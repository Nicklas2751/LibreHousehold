import {writable} from 'svelte/store';
import {CategoriesApi, type Category, Configuration} from '../../generated-sources/openapi';

const apiConfig = new Configuration({basePath: '/api'});
const api = new CategoriesApi(apiConfig);

export const categories = writable<Category[]>([]);

export async function loadCategories(householdId: string): Promise<void> {
    const result = await api.getCategories({householdId});
    categories.set(result);
}

export async function addCategory(householdId: string, category: Category): Promise<Category> {
    let savedCategory = await api.createCategory({householdId: householdId, category: category});
    categories.update((all) => [savedCategory, ...all]);
    return savedCategory;
}

export async function findCategory(householdId: string, categoryId: string): Promise<Category | undefined> {
    let found: Category | undefined;
    categories.subscribe(cats => {
        found = cats.find(c => c.id === categoryId);
    })();
    if (!found) {
        await loadCategories(householdId);
        categories.subscribe(cats => {
            found = cats.find(c => c.id === categoryId);
        })();
    }
    return found;
}


