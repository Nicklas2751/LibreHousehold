import { beforeEach, describe, expect, it, vi } from 'vitest';
import { get } from 'svelte/store';
import type { Category, CategoryUpdate } from '../../generated-sources/openapi';

const mockUpdateCategory = vi.hoisted(() => vi.fn());
const mockDeleteCategory = vi.hoisted(() => vi.fn());
const mockGetCategories = vi.hoisted(() => vi.fn());
const mockCreateCategory = vi.hoisted(() => vi.fn());

vi.mock('../../generated-sources/openapi', () => ({
	// eslint-disable-next-line @typescript-eslint/no-explicit-any
	Configuration: vi.fn(function (this: any) {}),
	// eslint-disable-next-line @typescript-eslint/no-explicit-any
	ExpensesApi: vi.fn(function (this: any) {
		return {
			getCategories: mockGetCategories,
			createCategory: mockCreateCategory,
			updateCategory: mockUpdateCategory,
			deleteCategory: mockDeleteCategory
		};
	})
}));

vi.mock('../../generated-sources/openapi/runtime', () => ({
	ResponseError: class ResponseError extends Error {
		name = 'ResponseError';
		response: Response;
		constructor(response: Response) {
			super();
			this.response = response;
		}
	}
}));

const mockAddToast = vi.hoisted(() => vi.fn());
vi.mock('./toastStore', () => ({
	addToast: mockAddToast
}));

import { ResponseError } from '../../generated-sources/openapi/runtime';
import { categories, updateCategory, deleteCategory } from './categoryStore';

describe('categoryStore', () => {
	beforeEach(() => {
		categories.set([]);
		vi.clearAllMocks();
	});

	describe('updateCategory', () => {
		it('apiSuccess_updatesStoreWithServerResponse', async () => {
			// given
			const categoryId = 'cat-1';
			const original: Category = { id: categoryId, name: 'Alt', icon: '🥕' };
			const update: CategoryUpdate = { name: 'Neu', icon: '🥦' };
			const updated: Category = { id: categoryId, name: 'Neu', icon: '🥦' };
			categories.set([original]);
			mockUpdateCategory.mockResolvedValueOnce(updated);

			// when
			const result = await updateCategory('household-1', categoryId, update);

			// then
			expect(result).toEqual(updated);
			expect(get(categories)).toEqual([updated]);
		});

		it('apiError_showsErrorToastAndDoesNotUpdateStore', async () => {
			// given
			const categoryId = 'cat-1';
			const original: Category = { id: categoryId, name: 'Alt' };
			const update: CategoryUpdate = { name: 'Neu' };
			categories.set([original]);
			mockUpdateCategory.mockRejectedValueOnce(new Error('Server error'));

			// when / then
			await expect(updateCategory('household-1', categoryId, update)).rejects.toThrow(
				'Server error'
			);
			expect(mockAddToast).toHaveBeenCalledOnce();
			expect(get(categories)).toEqual([original]);
		});
	});

	describe('deleteCategory', () => {
		it('apiSuccess_removesCategoryFromStore', async () => {
			// given
			const categoryId = 'cat-1';
			const category: Category = { id: categoryId, name: 'Lebensmittel' };
			const other: Category = { id: 'cat-2', name: 'Transport' };
			categories.set([category, other]);
			mockDeleteCategory.mockResolvedValueOnce(undefined);

			// when
			await deleteCategory('household-1', categoryId);

			// then
			expect(get(categories)).toEqual([other]);
		});

		it('apiError_rollsBackToOriginalStateAndShowsToast', async () => {
			// given
			const categoryId = 'cat-1';
			const category: Category = { id: categoryId, name: 'Lebensmittel' };
			categories.set([category]);
			mockDeleteCategory.mockRejectedValueOnce(new Error('Server error'));

			// when
			await deleteCategory('household-1', categoryId);

			// then
			expect(get(categories)).toEqual([category]);
			expect(mockAddToast).toHaveBeenCalledOnce();
		});

		it('categoryInUse_rollsBackAndShowsInUseToast', async () => {
			// given
			const categoryId = 'cat-1';
			const category: Category = { id: categoryId, name: 'Lebensmittel' };
			categories.set([category]);
			const inUseError = new ResponseError({ status: 409 } as Response);
			mockDeleteCategory.mockRejectedValueOnce(inUseError);

			// when
			await deleteCategory('household-1', categoryId);

			// then
			expect(get(categories)).toEqual([category]);
			expect(mockAddToast).toHaveBeenCalledOnce();
		});
	});
});
