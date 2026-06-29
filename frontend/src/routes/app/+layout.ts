import { browser } from '$app/environment';
import { get } from 'svelte/store';
import { isAuthenticated, login } from '$lib/stores/authStore.svelte';

export const ssr = false;

export async function load() {
	if (browser && !get(isAuthenticated)) {
		await login();
	}
}
