import { writable, get } from 'svelte/store';
import { setLocale, getLocale } from '$lib/paraglide/runtime.js';
import { Configuration, HouseholdApi } from '../../generated-sources/openapi';
import { householdState } from './householdState.svelte';
import { userState } from './userState';

const api = new HouseholdApi(new Configuration({ basePath: '/api' }));

export type Theme = 'light' | 'dark';
export type Language = 'en' | 'de';

export const theme = writable<Theme>('light');
export const language = writable<Language>('en');

function applyTheme(t: Theme) {
	document.documentElement.setAttribute('data-theme', t);
}

function detectInitialTheme(): Theme {
	const attr = document.documentElement.getAttribute('data-theme') as Theme | null;
	if (attr === 'light' || attr === 'dark') return attr;
	return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function initSettings() {
	const t = detectInitialTheme();
	const l = (getLocale() as Language) ?? 'en';

	theme.set(t);
	language.set(l);
}

function syncToApi(t: Theme, l: Language) {
	const household = get(householdState);
	const user = get(userState);
	if (!household?.id || !user?.id) return;

	api
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
	syncToApi(t, get(language));
}

export function setLanguage(l: Language) {
	language.set(l);
	setLocale(l);
	syncToApi(get(theme), l);
}
