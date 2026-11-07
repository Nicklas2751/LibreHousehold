import { describe, it, expect, vi, beforeEach } from 'vitest';
import { get } from 'svelte/store';
import { applyPreferences, setLanguage, theme } from './settingsStore';
import { householdState } from './householdState.svelte';
import { userState } from './userState';

const { mockGetLocale, mockSetLocale, mockUpdatePreferences } = vi.hoisted(() => ({
	mockGetLocale: vi.fn(),
	mockSetLocale: vi.fn(),
	mockUpdatePreferences: vi.fn()
}));

vi.mock('../../generated-sources/openapi', async (importOriginal) => {
	const original = await importOriginal<typeof import('../../generated-sources/openapi')>();
	return {
		...original,
		UsersettingsApi: vi.fn().mockImplementation(function (this: Record<string, unknown>) {
			this.updatePreferences = mockUpdatePreferences;
		})
	};
});

// setLocale reloads the page, which would abort the test run
vi.mock('#lib/paraglide/runtime.js', () => ({
	getLocale: mockGetLocale,
	setLocale: mockSetLocale
}));

describe('applyPreferences', () => {
	beforeEach(() => {
		vi.clearAllMocks();
		delete document.documentElement.dataset.theme;
		theme.set('light');
		mockGetLocale.mockReturnValue('en');
	});

	it('storedTheme_appliesThemeToDocument', () => {
		// when
		applyPreferences({ theme: 'dark' });

		// then
		expect(document.documentElement.dataset.theme).toBe('dark');
	});

	it('storedTheme_updatesThemeStore', () => {
		// when
		applyPreferences({ theme: 'dark' });

		// then
		expect(get(theme)).toBe('dark');
	});

	it('noStoredTheme_leavesBrowserDefault', () => {
		// when
		applyPreferences({});

		// then
		expect(document.documentElement.dataset.theme).toBeUndefined();
	});

	it('storedLanguageDiffersFromCurrentLocale_setsLocale', () => {
		// when
		applyPreferences({ language: 'de' });

		// then
		expect(mockSetLocale).toHaveBeenCalledWith('de');
	});

	it.each([
		{ desc: 'storedLanguageEqualsCurrentLocale', preferences: { language: 'en' as const } },
		{ desc: 'noStoredLanguage', preferences: {} }
	])('$desc_doesNotSetLocale', ({ preferences }) => {
		// when
		applyPreferences(preferences);

		// then
		expect(mockSetLocale).not.toHaveBeenCalled();
	});
});

describe('setLanguage', () => {
	beforeEach(() => {
		vi.clearAllMocks();
		householdState.set({ id: 'household-id', name: 'Household' });
		userState.set({ id: 'member-id', name: 'Max', email: 'max@example.com' });
	});

	it('signedIn_savesPreferenceBeforeReloadingWithNewLocale', async () => {
		// given
		mockUpdatePreferences.mockResolvedValue({});

		// when
		await setLanguage('de');

		// then
		expect(mockUpdatePreferences.mock.invocationCallOrder[0]).toBeLessThan(
			mockSetLocale.mock.invocationCallOrder[0]
		);
	});

	it('savingPreferenceFails_stillSetsLocale', async () => {
		// given
		mockUpdatePreferences.mockRejectedValue(new Error('network error'));

		// when
		await setLanguage('de');

		// then
		expect(mockSetLocale).toHaveBeenCalledWith('de');
	});
});
