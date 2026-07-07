import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';

const {
	mockHandleCallback,
	mockGetUser,
	mockGoto,
	mockSetupHousehold,
	mockJoinHouseholdAuthenticated
} = vi.hoisted(() => ({
	mockHandleCallback: vi.fn(),
	mockGetUser: vi.fn(),
	mockGoto: vi.fn(),
	mockSetupHousehold: vi.fn(),
	mockJoinHouseholdAuthenticated: vi.fn()
}));

vi.mock('$app/navigation', () => ({ goto: mockGoto }));

vi.mock('$lib/stores/authStore.svelte', async (importOriginal) => {
	const original = await importOriginal<typeof import('../../lib/stores/authStore.svelte')>();
	return { ...original, handleCallback: mockHandleCallback, getUser: mockGetUser };
});

vi.mock('../../generated-sources/openapi', async (importOriginal) => {
	const original = await importOriginal<typeof import('../../generated-sources/openapi')>();
	return {
		...original,
		HouseholdApi: vi.fn().mockImplementation(function (this: Record<string, unknown>) {
			this.setupHousehold = mockSetupHousehold;
		}),
		MembersApi: vi.fn().mockImplementation(function (this: Record<string, unknown>) {
			this.joinHouseholdAuthenticated = mockJoinHouseholdAuthenticated;
		})
	};
});

vi.mock('../../lib/paraglide/runtime.js', async (importOriginal) => {
	const original = await importOriginal<typeof import('../../lib/paraglide/runtime.js')>();
	return { ...original, getLocale: () => 'de' as const, setLocale: vi.fn() };
});

import Page from './+page.svelte';

describe('Callback Page', () => {
	beforeEach(() => {
		vi.clearAllMocks();
		mockHandleCallback.mockResolvedValue(undefined);
	});

	afterEach(() => {
		sessionStorage.removeItem('lh_pending_setup');
		sessionStorage.removeItem('lh_pending_join');
	});

	it('noPendingState_navigatesToDashboard', async () => {
		// given / when
		render(Page);
		await vi.waitFor(() => expect(mockGoto).toHaveBeenCalled());

		// then
		expect(mockGoto).toHaveBeenCalledWith('/app/dashboard');
	});

	it('pendingSetup_callsSetupHouseholdAndShowsInviteLink', async () => {
		// given
		sessionStorage.setItem(
			'lh_pending_setup',
			JSON.stringify({ householdName: 'Die Müllers', householdImage: '', memberName: 'Max' })
		);
		mockGetUser.mockReturnValue({ profile: { sub: 'account-id' } });
		mockSetupHousehold.mockResolvedValue({
			household: { id: 'household-id', name: 'Die Müllers' },
			inviteToken: 'invite-token-123',
			inviteValidUntil: '2099-12-31'
		});

		// when
		render(Page);

		// then
		await expect.element(page.getByText('Haushalt eingerichtet!')).toBeVisible();
		expect(mockSetupHousehold).toHaveBeenCalledWith(
			expect.objectContaining({
				householdSetup: expect.objectContaining({
					household: expect.objectContaining({ name: 'Die Müllers' }),
					member: expect.objectContaining({ id: 'account-id', name: 'Max' })
				})
			})
		);
		expect(sessionStorage.getItem('lh_pending_setup')).toBeNull();
	});

	it('pendingJoin_callsJoinHouseholdAuthenticatedWithStashedData', async () => {
		// given
		sessionStorage.setItem(
			'lh_pending_join',
			JSON.stringify({
				token: 'invite-token',
				memberName: 'Dex User',
				memberAvatar: '',
				householdId: 'household-id',
				householdName: 'Die Müllers'
			})
		);
		mockJoinHouseholdAuthenticated.mockResolvedValue({
			id: 'account-id',
			name: 'Dex User',
			isAdmin: false
		});

		// when
		render(Page);
		await vi.waitFor(() => expect(mockJoinHouseholdAuthenticated).toHaveBeenCalled());

		// then
		expect(mockJoinHouseholdAuthenticated).toHaveBeenCalledWith({
			householdJoin: { token: 'invite-token', memberName: 'Dex User', memberAvatar: undefined }
		});
	});

	it('pendingJoin_onSuccess_showsJoinSuccessPanelWithoutInviteWidgets', async () => {
		// given
		sessionStorage.setItem(
			'lh_pending_join',
			JSON.stringify({
				token: 'invite-token',
				memberName: 'Dex User',
				memberAvatar: '',
				householdId: 'household-id',
				householdName: 'Die Müllers'
			})
		);
		mockJoinHouseholdAuthenticated.mockResolvedValue({
			id: 'account-id',
			name: 'Dex User',
			isAdmin: false
		});

		// when
		render(Page);

		// then
		await expect.element(page.getByText('Erfolgreich beigetreten')).toBeVisible();
		expect(page.getByText('Haushalt eingerichtet!').elements().length).toBe(0);
	});

	it('pendingJoin_onSuccess_clearsSessionStorageKey', async () => {
		// given
		sessionStorage.setItem(
			'lh_pending_join',
			JSON.stringify({
				token: 'invite-token',
				memberName: 'Dex User',
				memberAvatar: '',
				householdId: 'household-id',
				householdName: 'Die Müllers'
			})
		);
		mockJoinHouseholdAuthenticated.mockResolvedValue({
			id: 'account-id',
			name: 'Dex User',
			isAdmin: false
		});

		// when
		render(Page);
		await vi.waitFor(() => expect(sessionStorage.getItem('lh_pending_join')).toBeNull());

		// then
		expect(sessionStorage.getItem('lh_pending_join')).toBeNull();
	});

	it('pendingJoin_apiError_showsErrorMessage', async () => {
		// given
		sessionStorage.setItem(
			'lh_pending_join',
			JSON.stringify({
				token: 'invite-token',
				memberName: 'Dex User',
				memberAvatar: '',
				householdId: 'household-id',
				householdName: 'Die Müllers'
			})
		);
		mockJoinHouseholdAuthenticated.mockRejectedValue(new Error('Conflict'));

		// when
		render(Page);

		// then
		await expect.element(page.getByRole('alert')).toBeVisible();
	});
});
