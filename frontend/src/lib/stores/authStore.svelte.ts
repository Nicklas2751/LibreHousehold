import { UserManager, type User, WebStorageStateStore } from 'oidc-client-ts';
import { browser } from '$app/environment';
import { writable, derived, get } from 'svelte/store';

const AUTHORITY = browser ? (import.meta.env.VITE_AUTHORITY ?? window.location.origin) : '';

const userManager = browser
	? new UserManager({
			authority: AUTHORITY,
			client_id: 'librehousehold-spa',
			redirect_uri: window.location.origin + '/callback',
			scope: 'openid profile',
			userStore: new WebStorageStateStore({ store: window.sessionStorage })
		})
	: null;

const userStore = writable<User | null>(null);

export const isAuthenticated = derived(userStore, (u) => u !== null);

export async function login(): Promise<void> {
	await userManager?.signinRedirect();
}

export async function handleCallback(): Promise<void> {
	if (!userManager) return;
	const user = await userManager.signinRedirectCallback();
	userStore.set(user);
	sessionStorage.removeItem('lh_pkce_started');
}

export async function logout(): Promise<void> {
	await userManager?.removeUser();
	userStore.set(null);
}

export function getAccessToken(): string | null {
	return get(userStore)?.access_token ?? null;
}

export function getUser(): User | null {
	return get(userStore);
}

export async function restoreUser(): Promise<User | null> {
	if (!userManager) return null;
	const stored = await userManager.getUser();
	if (stored && !stored.expired) {
		userStore.set(stored);
		return stored;
	}
	return null;
}
