import { describe, it, expect } from 'vitest';
import { session, setAuthenticated, setGuest } from './sessionState.svelte';
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
			preferences: {}
		};

		// when
		setAuthenticated(user);

		// then
		expect(session.status).toBe('authenticated');
		expect(session.currentUser).toEqual(user);
	});

	it('setGuest sets status to guest and currentUser to null', () => {
		// when
		setGuest();

		// then
		expect(session.status).toBe('guest');
		expect(session.currentUser).toBeNull();
	});
});
