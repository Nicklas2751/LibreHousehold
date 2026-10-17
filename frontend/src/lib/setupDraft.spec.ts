import { describe, it, expect, vi, afterEach } from 'vitest';
import { saveSetupDraft, takeSetupDraft, type SetupDraft } from './setupDraft';

function createFakeStorage(options: { maxValueLength?: number } = {}): Storage {
	const entries = new Map<string, string>();
	return {
		getItem: (key: string) => entries.get(key) ?? null,
		setItem: (key: string, value: string) => {
			if (options.maxValueLength !== undefined && value.length > options.maxValueLength) {
				throw new DOMException('quota exceeded', 'QuotaExceededError');
			}
			entries.set(key, value);
		},
		removeItem: (key: string) => {
			entries.delete(key);
		},
		clear: () => entries.clear(),
		key: () => null,
		get length() {
			return entries.size;
		}
	};
}

describe('setupDraft', () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it('takeSetupDraft_afterSave_returnsSavedDraft', () => {
		// given
		vi.stubGlobal('sessionStorage', createFakeStorage());
		const draft: SetupDraft = {
			householdId: 'household-id',
			householdName: 'Die Testfamilie',
			householdImage: 'data:image/png;base64,abc'
		};
		saveSetupDraft(draft);

		// when
		const restored = takeSetupDraft();

		// then
		expect(restored).toEqual(draft);
	});

	it('takeSetupDraft_calledTwice_returnsNullTheSecondTime', () => {
		// given
		vi.stubGlobal('sessionStorage', createFakeStorage());
		saveSetupDraft({
			householdId: 'household-id',
			householdName: 'Die Testfamilie',
			householdImage: ''
		});
		takeSetupDraft();

		// when
		const restored = takeSetupDraft();

		// then
		expect(restored).toBeNull();
	});

	it('takeSetupDraft_malformedValue_returnsNull', () => {
		// given
		const storage = createFakeStorage();
		storage.setItem('librehousehold.setupDraft', '{not json');
		vi.stubGlobal('sessionStorage', storage);

		// when
		const restored = takeSetupDraft();

		// then
		expect(restored).toBeNull();
	});

	it('saveSetupDraft_imageExceedsStorageQuota_keepsDraftWithoutImage', () => {
		// given
		vi.stubGlobal('sessionStorage', createFakeStorage({ maxValueLength: 200 }));
		saveSetupDraft({
			householdId: 'household-id',
			householdName: 'Die Testfamilie',
			householdImage: 'data:image/png;base64,' + 'a'.repeat(500)
		});

		// when
		const restored = takeSetupDraft();

		// then
		expect(restored).toEqual({
			householdId: 'household-id',
			householdName: 'Die Testfamilie',
			householdImage: ''
		});
	});

	it('saveSetupDraft_storageUnavailable_doesNotThrow', () => {
		// given
		vi.stubGlobal('sessionStorage', {
			setItem: () => {
				throw new DOMException('denied', 'SecurityError');
			}
		});

		// when / then
		expect(() =>
			saveSetupDraft({
				householdId: 'household-id',
				householdName: 'Die Testfamilie',
				householdImage: ''
			})
		).not.toThrow();
	});
});
