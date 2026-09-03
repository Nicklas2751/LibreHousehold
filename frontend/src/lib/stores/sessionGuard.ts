import type { SessionStatus } from './sessionState.svelte';

export function shouldRedirectToLogin(status: SessionStatus): boolean {
	return status === 'guest';
}

export function shouldTreatAsSessionExpiry(status: SessionStatus): boolean {
	return status === 'authenticated';
}
