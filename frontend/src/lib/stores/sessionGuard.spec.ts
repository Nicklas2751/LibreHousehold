import { describe, expect, it } from 'vitest';
import {
	loginRedirectTarget,
	shouldRedirectToLogin,
	shouldTreatAsSessionExpiry
} from './sessionGuard';
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

describe('loginRedirectTarget', () => {
	it.each([
		{
			expired: false,
			expected: '/login?returnTo=%2Fapp%2Ftasks%3Ffilter%3Dopen'
		},
		{
			expired: true,
			expected: '/login?returnTo=%2Fapp%2Ftasks%3Ffilter%3Dopen&reason=session-expired'
		}
	])('expired $expired — returns $expected', ({ expired, expected }) => {
		// when
		const result = loginRedirectTarget(
			new URL('http://localhost:5173/app/tasks?filter=open'),
			expired
		);

		// then
		expect(result).toBe(expected);
	});
});
