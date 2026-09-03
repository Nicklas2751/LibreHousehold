import { describe, it, expect, vi, beforeEach } from 'vitest';
import { get } from 'svelte/store';
import { bootstrapSession } from './sessionBootstrap';
import { session } from './sessionState.svelte';
import { householdState } from './householdState.svelte';
import { userState } from './userState';

const { mockGetCurrentUser, mockAddToast } = vi.hoisted(() => ({
	mockGetCurrentUser: vi.fn(),
	mockAddToast: vi.fn()
}));

vi.mock('../../generated-sources/openapi', async (importOriginal) => {
	const original = await importOriginal<typeof import('../../generated-sources/openapi')>();
	return {
		...original,
		SessionApi: vi.fn().mockImplementation(function (this: Record<string, unknown>) {
			this.getCurrentUser = mockGetCurrentUser;
		})
	};
});

vi.mock('$lib/stores/toastStore', () => ({ addToast: mockAddToast }));

const currentUser = {
	member: { id: 'member-id', name: 'Max Mustermann', email: 'max@example.com', isAdmin: true },
	household: { id: 'household-id', name: 'Die Testfamilie' },
	preferences: {}
};

describe('bootstrapSession', () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	it('on 200 — sets session to authenticated with the returned CurrentUser', async () => {
		// given
		mockGetCurrentUser.mockResolvedValue(currentUser);

		// when
		await bootstrapSession();

		// then
		expect(session.status).toBe('authenticated');
		expect(session.currentUser).toEqual(currentUser);
		expect(get(householdState)).toEqual(currentUser.household);
		expect(get(userState)).toEqual(currentUser.member);
	});

	it('on 401 — sets session to guest, without showing a toast', async () => {
		// given
		mockGetCurrentUser.mockRejectedValue({ status: 401 });

		// when
		await bootstrapSession();

		// then
		expect(session.status).toBe('guest');
		expect(mockAddToast).not.toHaveBeenCalled();
	});

	it('on an unexpected error — sets session to guest and shows a toast', async () => {
		// given
		mockGetCurrentUser.mockRejectedValue(new Error('network error'));

		// when
		await bootstrapSession();

		// then
		expect(session.status).toBe('guest');
		expect(mockAddToast).toHaveBeenCalled();
	});
});
