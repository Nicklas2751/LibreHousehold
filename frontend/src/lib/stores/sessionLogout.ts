import { goto } from '$app/navigation';
import { apiConfiguration } from '$lib/api/httpClient';
import { SessionApi } from '../../generated-sources/openapi';
import { setGuest } from './sessionState.svelte';
import { householdState } from './householdState.svelte';
import { userState } from './userState';

const sessionApi = new SessionApi(apiConfiguration);

export async function logout(): Promise<void> {
	try {
		await sessionApi.logout();
	} catch {
		// Reset local state even if the request itself fails, so the user is never left
		// hanging in an inconsistent UI state — /logout is idempotent per contract anyway.
	}
	setGuest();
	householdState.set(undefined);
	userState.set(undefined);
	await goto('/');
}
