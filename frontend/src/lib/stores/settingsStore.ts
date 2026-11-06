import { get, writable } from 'svelte/store';
import { getLocale, setLocale } from '$lib/paraglide/runtime.js';
import { type UserPreferences, UsersettingsApi } from '../../generated-sources/openapi';
import { apiConfiguration } from '$lib/api/httpClient';
import { householdState } from './householdState.svelte';
import { userState } from './userState';

const api = new UsersettingsApi(apiConfiguration);

export type Theme = 'light' | 'dark';
export type Language = 'en' | 'de';

export const theme = writable<Theme>('light');
export const language = writable<Language>('en');

function applyTheme(t: Theme) {
	document.documentElement.dataset.theme = t;
}

function detectInitialTheme(): Theme {
	const attr = document.documentElement.dataset.theme as Theme | null;
	if (attr === 'light' || attr === 'dark') return attr;
	return globalThis.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function initSettings() {
	const t = detectInitialTheme();
	const l = (getLocale() as Language) ?? 'en';

	theme.set(t);
	language.set(l);
}

/**
 * Applies the preferences stored for the signed-in member. Whatever the member has not chosen yet
 * stays as the browser decided it, so the browser only provides the initial setting.
 */
export function applyPreferences(preferences: UserPreferences) {
	if (preferences.theme) {
		theme.set(preferences.theme);
		applyTheme(preferences.theme);
	}
	// setLocale reloads the page, so it must only run when the locale actually changes
	if (preferences.language && preferences.language !== getLocale()) {
		language.set(preferences.language);
		setLocale(preferences.language);
	}
}

async function syncToApi(t: Theme, l: Language): Promise<void> {
	const household = get(householdState);
	const user = get(userState);
	if (!household?.id || !user?.id) return;

	await api
		.updatePreferences({
			householdId: household.id,
			memberId: user.id,
			userPreferences: { theme: t, language: l }
		})
		.catch(() => {});
}

export function setTheme(t: Theme) {
	theme.set(t);
	applyTheme(t);
	void syncToApi(t, get(language));
}

export async function setLanguage(l: Language): Promise<void> {
	language.set(l);
	// setLocale reloads the page, which could abort a request still in flight
	await syncToApi(get(theme), l);
	setLocale(l);
}
