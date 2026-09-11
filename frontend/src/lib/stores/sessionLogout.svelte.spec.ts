import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { get } from 'svelte/store';
import { logout } from './sessionLogout';
import { session, setAuthenticated } from './sessionState.svelte';
import { householdState } from './householdState.svelte';
import { userState } from './userState';

const { mockGoto } = vi.hoisted(() => ({ mockGoto: vi.fn() }));

vi.mock('$app/navigation', () => ({ goto: mockGoto }));

const currentUser = {
	member: { id: 'member-id', name: 'Max Mustermann', email: 'max@example.com', isAdmin: true },
	household: { id: 'household-id', name: 'Die Testfamilie' },
	preferences: {},
	emailVerified: true
};

function clearCookies() {
	document.cookie.split(';').forEach((cookie) => {
		const name = cookie.split('=')[0].trim();
		if (name) {
			document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
		}
	});
}

describe('logout', () => {
	beforeEach(() => {
		vi.clearAllMocks();
		clearCookies();
		document.cookie = 'XSRF-TOKEN=test-csrf-token';
		setAuthenticated(currentUser);
		householdState.set(currentUser.household);
		userState.set(currentUser.member);
	});

	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it('posts to the unprefixed /logout endpoint with the CSRF header, sets the session to guest and navigates to /', async () => {
		// given
		const mockFetch = vi.fn().mockResolvedValue({ ok: true } as Response);
		vi.stubGlobal('fetch', mockFetch);

		// when
		await logout();

		// then
		expect(mockFetch).toHaveBeenCalledWith(
			'/logout',
			expect.objectContaining({
				method: 'POST',
				credentials: 'include',
				headers: { 'X-XSRF-TOKEN': 'test-csrf-token' }
			})
		);
		expect(session.status).toBe('guest');
		expect(get(householdState)).toBeUndefined();
		expect(get(userState)).toBeUndefined();
		expect(mockGoto).toHaveBeenCalledWith('/');
	});

	it('still sets the session to guest and navigates to / when the logout request fails', async () => {
		// given
		const mockFetch = vi.fn().mockRejectedValue(new Error('network error'));
		vi.stubGlobal('fetch', mockFetch);

		// when
		await logout();

		// then
		expect(session.status).toBe('guest');
		expect(get(householdState)).toBeUndefined();
		expect(get(userState)).toBeUndefined();
		expect(mockGoto).toHaveBeenCalledWith('/');
	});
});
