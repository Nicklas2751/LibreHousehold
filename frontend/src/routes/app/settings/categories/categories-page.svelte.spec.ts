import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';

const {
	mockGetCategories,
	mockCreateCategory,
	mockUpdateCategory,
	mockDeleteCategory,
	mockAddToast,
	HOUSEHOLD_ID,
	CATEGORY_ID
} = vi.hoisted(() => ({
	mockGetCategories: vi.fn(),
	mockCreateCategory: vi.fn(),
	mockUpdateCategory: vi.fn(),
	mockDeleteCategory: vi.fn(),
	mockAddToast: vi.fn(),
	HOUSEHOLD_ID: '00000000-0000-0000-0000-000000000001',
	CATEGORY_ID: '00000000-0000-0000-0000-000000000011'
}));

vi.mock('../../../../generated-sources/openapi', async (importOriginal) => {
	const original = await importOriginal<typeof import('../../../../generated-sources/openapi')>();
	return {
		...original,
		ExpensesApi: vi.fn().mockImplementation(function (this: Record<string, unknown>) {
			this.getCategories = mockGetCategories;
			this.createCategory = mockCreateCategory;
			this.updateCategory = mockUpdateCategory;
			this.deleteCategory = mockDeleteCategory;
		})
	};
});

vi.mock('$lib/stores/householdState.svelte', () => ({
	householdState: {
		subscribe(run: (value: { id: string; name: string }) => void) {
			run({ id: HOUSEHOLD_ID, name: 'Test' });
			return () => {};
		}
	}
}));

vi.mock('$lib/stores/toastStore', () => ({ addToast: mockAddToast }));

import { categories } from '$lib/stores/categoryStore';
import Page from './+page.svelte';

describe('categories page', () => {
	beforeEach(() => {
		categories.set([]);
		vi.clearAllMocks();
	});

	it('createCategory_newNameAndIcon_appearsInList', async () => {
		// given
		const newCategory = { id: 'new-id', name: 'Transport', icon: '🚗' };
		mockGetCategories.mockResolvedValue([]);
		mockCreateCategory.mockResolvedValue(newCategory);
		render(Page);
		await expect.element(page.getByText('No categories yet.')).toBeVisible();

		// when
		await page.getByPlaceholder('e.g. Groceries').fill('Transport');
		await page.getByRole('textbox', { name: 'Icon (Emoji)' }).fill('🚗');
		await page.getByRole('button', { name: 'Create category' }).click();

		// then
		await expect.element(page.getByText('Transport')).toBeVisible();
		expect(mockAddToast).toHaveBeenCalledWith(expect.objectContaining({ type: 'success' }));
	});

	it('editCategory_changingName_updatesListEntry', async () => {
		// given
		const existing = { id: CATEGORY_ID, name: 'Groceries', icon: '🥕' };
		const updated = { id: CATEGORY_ID, name: 'Fresh Groceries', icon: '🥦' };
		mockGetCategories.mockResolvedValue([existing]);
		mockUpdateCategory.mockResolvedValue(updated);
		render(Page);
		await expect.element(page.getByText('Groceries')).toBeVisible();

		// when
		await page.getByRole('button', { name: 'Edit category' }).click();
		await page.getByRole('textbox', { name: 'Name' }).nth(1).fill('Fresh Groceries');
		await page.getByRole('button', { name: 'Save' }).click();

		// then
		await expect.element(page.getByText('Fresh Groceries')).toBeVisible();
		expect(mockAddToast).toHaveBeenCalledWith(expect.objectContaining({ type: 'success' }));
	});

	it('deleteCategory_confirming_removedFromList', async () => {
		// given
		const existing = { id: CATEGORY_ID, name: 'Groceries', icon: '🥕' };
		mockGetCategories.mockResolvedValue([existing]);
		mockDeleteCategory.mockResolvedValue(undefined);
		render(Page);
		await expect.element(page.getByText('Groceries')).toBeVisible();

		// when
		await page.getByRole('button', { name: 'Delete category?' }).click();
		await page.getByRole('button', { name: 'Delete', exact: true }).click();

		// then
		await expect.element(page.getByText('No categories yet.')).toBeVisible();
	});

	it('createCategory_duplicateName_showsErrorToast', async () => {
		// given
		const existing = { id: CATEGORY_ID, name: 'Groceries', icon: '🥕' };
		mockGetCategories.mockResolvedValue([existing]);
		mockCreateCategory.mockRejectedValue(new Error('Conflict'));
		render(Page);
		await expect.element(page.getByText('Groceries')).toBeVisible();

		// when
		await page.getByPlaceholder('e.g. Groceries').fill('Groceries');
		await page.getByRole('button', { name: 'Create category' }).click();

		// then
		expect(mockAddToast).toHaveBeenCalledWith(expect.objectContaining({ type: 'error' }));
	});
});
