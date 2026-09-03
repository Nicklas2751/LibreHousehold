import { describe, expect, it } from 'vitest';
import { shouldRedirectToLogin, shouldTreatAsSessionExpiry } from './sessionGuard';
import type { SessionStatus } from './sessionState.svelte';

describe('shouldRedirectToLogin', () => {
	it.each<SessionStatus>(['guest'])('returns true for status %s', (status) => {
		// when
		const result = shouldRedirectToLogin(status);

		// then
		expect(result).toBe(true);
	});

	it.each<SessionStatus>(['authenticated', 'bootstrapping'])(
		'returns false for status %s',
		(status) => {
			// when
			const result = shouldRedirectToLogin(status);

			// then
			expect(result).toBe(false);
		}
	);
});

describe('shouldTreatAsSessionExpiry', () => {
	it.each<SessionStatus>(['authenticated'])('returns true for status %s', (status) => {
		// when
		const result = shouldTreatAsSessionExpiry(status);

		// then
		expect(result).toBe(true);
	});

	it.each<SessionStatus>(['guest', 'bootstrapping'])('returns false for status %s', (status) => {
		// when
		const result = shouldTreatAsSessionExpiry(status);

		// then
		expect(result).toBe(false);
	});
});
