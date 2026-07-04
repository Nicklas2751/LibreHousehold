import { browser } from '$app/environment';
import { get } from 'svelte/store';
import { redirect } from '@sveltejs/kit';
import { isAuthenticated, login, restoreUser, getUser } from '$lib/stores/authStore.svelte';
import { householdState, updateHouseholdState } from '$lib/stores/householdState.svelte';
import { userState, updateUserState } from '$lib/stores/userState';
import { MembersApi } from '../../generated-sources/openapi';
import { createApiConfig } from '$lib/api';

export const ssr = false;

export async function load() {
	if (!browser) return;

	if (!get(isAuthenticated)) {
		const restored = await restoreUser();
		if (!restored) {
			try {
				await login();
			} catch {
				throw redirect(302, '/login');
			}
			return;
		}
	}

	if (get(householdState) && get(userState)) return;

	const user = getUser();
	if (!user?.access_token) return;

	try {
		const payload = JSON.parse(atob(user.access_token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
		const householdId: string = payload.household_id;
		const memberId: string = payload.sub;
		if (!householdId || !memberId) return;

		if (!get(householdState)) {
			updateHouseholdState({ id: householdId, name: '' });
		}
		if (!get(userState)) {
			const membersApi = new MembersApi(createApiConfig());
			const member = await membersApi.getMember({ householdId, memberId });
			updateUserState(member);
		}
	} catch {
		// Store rehydration failed — continue without user data.
		// Page components handle the empty-state gracefully.
		// Do NOT redirect to /login here: a redirect loop with the login
		// page's auto-PKCE would result if the API call fails transiently.
	}
}
