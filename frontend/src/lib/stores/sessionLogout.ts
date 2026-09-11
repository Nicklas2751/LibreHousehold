import { goto } from '$app/navigation';
import { getCsrfTokenFromCookieHeader } from '$lib/api/csrf';
import { setGuest } from './sessionState.svelte';
import { householdState } from './householdState.svelte';
import { userState } from './userState';

export async function logout(): Promise<void> {
	try {
		// Deliberately a raw fetch against the unprefixed /logout path, not the generated
		// SessionApi client: /logout is a Spring Security framework endpoint served outside the
		// business API's /v1 base path (see SecurityConfig, same reasoning as the raw /login POST
		// in login/+page.svelte), whereas apiConfiguration's basePath (/api) is rewritten by the
		// dev proxy to the /v1-prefixed backend path — a mismatch that previously made this call
		// hit a generated-but-unimplemented 501 stub instead of ever reaching the real endpoint.
		const csrfToken = getCsrfTokenFromCookieHeader(document.cookie);
		await fetch('/logout', {
			method: 'POST',
			credentials: 'include',
			headers: csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}
		});
	} catch {
		// Reset local state even if the request itself fails, so the user is never left
		// hanging in an inconsistent UI state — /logout is idempotent per contract anyway.
	}
	setGuest();
	householdState.set(undefined);
	userState.set(undefined);
	await goto('/');
}
