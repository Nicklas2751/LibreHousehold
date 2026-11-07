import type { SessionStatus } from './sessionState.svelte';

export function shouldRedirectToLogin(status: SessionStatus): boolean {
	return status === 'guest';
}

export function shouldTreatAsSessionExpiry(status: SessionStatus): boolean {
	return status === 'authenticated';
}

const SESSION_EXPIRED_REASON = 'session-expired';

export function loginRedirectTarget(
	currentUrl: Pick<URL, 'pathname' | 'search'>,
	expired: boolean
): string {
	const query = new URLSearchParams({ returnTo: currentUrl.pathname + currentUrl.search });
	if (expired) {
		query.set('reason', SESSION_EXPIRED_REASON);
	}
	return `/login?${query}`;
}
