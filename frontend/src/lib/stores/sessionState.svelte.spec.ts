import { describe, it, expect } from 'vitest';
import { session, setAuthenticated, setGuest, setSessionExpired } from './sessionState.svelte';
import type { CurrentUser } from '../../generated-sources/openapi';

describe('sessionState', () => {
	it('starts in the bootstrapping status', () => {
		// then
		expect(session.status).toBe('bootstrapping');
	});

	it('setAuthenticated sets status to authenticated and currentUser to the given user', () => {
		// given
		const user: CurrentUser = {
			member: { id: 'member-id', name: 'Max Mustermann', email: 'max@example.com', isAdmin: true },
			household: { id: 'household-id', name: 'Die Testfamilie' },
			preferences: {},
			emailVerified: true,
			socialLoginOnly: false,
			sessionTimeoutSeconds: 1800
		};

		// when
		setAuthenticated(user);

		// then
		expect(session.status).toBe('authenticated');
		expect(session.currentUser).toEqual(user);
	});

	it('setGuest sets status to guest, currentUser to null and clears the expiry', () => {
		// given
		setSessionExpired();

		// when
		setGuest();

		// then
		expect({ ...session }).toEqual({ status: 'guest', currentUser: null, expired: false });
	});

	it('setSessionExpired sets status to guest, currentUser to null and marks the session as expired', () => {
		// when
		setSessionExpired();

		// then
		expect({ ...session }).toEqual({ status: 'guest', currentUser: null, expired: true });
	});
});
