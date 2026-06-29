import { browser } from '$app/environment';
import { get } from 'svelte/store';
import { redirect } from '@sveltejs/kit';
import { isAuthenticated, login } from '$lib/stores/authStore.svelte';

export const ssr = false;

export async function load() {
	if (browser && !get(isAuthenticated)) {
		try {
			await login();
		} catch {
			throw redirect(302, '/login');
		}
	}
}
