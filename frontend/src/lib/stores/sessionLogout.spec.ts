import { describe, it, expect, vi, beforeEach } from 'vitest';
import { get } from 'svelte/store';
import { logout } from './sessionLogout';
import { session, setAuthenticated } from './sessionState.svelte';
import { householdState } from './householdState.svelte';
import { userState } from './userState';

const { mockLogout, mockGoto } = vi.hoisted(() => ({
	mockLogout: vi.fn(),
	mockGoto: vi.fn()
}));

vi.mock('../../generated-sources/openapi', async (importOriginal) => {
	const original = await importOriginal<typeof import('../../generated-sources/openapi')>();
	return {
		...original,
		SessionApi: vi.fn().mockImplementation(function (this: Record<string, unknown>) {
			this.logout = mockLogout;
		})
	};
});

vi.mock('$app/navigation', () => ({ goto: mockGoto }));

const currentUser = {
	member: { id: 'member-id', name: 'Max Mustermann', email: 'max@example.com', isAdmin: true },
	household: { id: 'household-id', name: 'Die Testfamilie' },
	preferences: {}
};

describe('logout', () => {
	beforeEach(() => {
		vi.clearAllMocks();
		setAuthenticated(currentUser);
		householdState.set(currentUser.household);
		userState.set(currentUser.member);
	});

	it('calls SessionApi.logout, sets the session to guest and navigates to /', async () => {
		// given
		mockLogout.mockResolvedValue(undefined);

		// when
		await logout();

		// then
		expect(mockLogout).toHaveBeenCalled();
		expect(session.status).toBe('guest');
		expect(get(householdState)).toBeUndefined();
		expect(get(userState)).toBeUndefined();
		expect(mockGoto).toHaveBeenCalledWith('/');
	});

	it('still sets the session to guest and navigates to / when the logout request fails', async () => {
		// given
		mockLogout.mockRejectedValue(new Error('network error'));

		// when
		await logout();

		// then
		expect(session.status).toBe('guest');
		expect(get(householdState)).toBeUndefined();
		expect(get(userState)).toBeUndefined();
		expect(mockGoto).toHaveBeenCalledWith('/');
	});
});
