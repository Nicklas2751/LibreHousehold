import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import SettingsUserPage from './+page.svelte';
import { userState } from '$lib/stores/userState';
import { householdState } from '$lib/stores/householdState.svelte';
import { setAuthenticated, setGuest } from '$lib/stores/sessionState.svelte';
import { members } from '$lib/stores/memberStore';
import type { CurrentUser } from '../../../../generated-sources/openapi';

const { mockUpdateMember, mockChangePassword } = vi.hoisted(() => ({
	mockUpdateMember: vi.fn(),
	mockChangePassword: vi.fn()
}));

vi.mock('../../../../generated-sources/openapi', async (importOriginal) => {
	const original = await importOriginal<typeof import('../../../../generated-sources/openapi')>();
	return {
		...original,
		MembersApi: vi.fn().mockImplementation(function (this: Record<string, unknown>) {
			this.updateMember = mockUpdateMember;
			this.changePassword = mockChangePassword;
			// Must resolve a non-empty list: the component's own $effect reloads members whenever
			// $members.length === 0, so an empty result here would retrigger it in an infinite loop.
			this.getMembers = vi
				.fn()
				.mockResolvedValue([
					{ id: 'member-1', name: 'Max', email: 'max@example.com', isAdmin: false }
				]);
		}),
		HouseholdApi: vi.fn().mockImplementation(function () {})
	};
});

vi.mock('$lib/stores/toastStore', () => ({ addToast: vi.fn() }));

vi.mock('$lib/paraglide/runtime.js', async (importOriginal) => {
	const original = await importOriginal<typeof import('$lib/paraglide/runtime.js')>();
	return { ...original, getLocale: () => 'de' as const, setLocale: vi.fn() };
});

function currentUser(overrides: Partial<CurrentUser> = {}): CurrentUser {
	return {
		member: { id: 'member-1', name: 'Max', email: 'max@example.com', isAdmin: false },
		household: { id: 'household-1', name: 'Musterhaushalt' },
		preferences: { theme: 'light', language: 'de' },
		emailVerified: true,
		...overrides
	};
}

describe('settings user page - email verification lock', () => {
	beforeEach(() => {
		vi.clearAllMocks();
		setGuest();
		userState.set(undefined);
		householdState.set(undefined);
		members.set([]);
	});

	it('emailVerified false — disables the email input, password fields/button, and shows both hints', async () => {
		// given
		const user = currentUser({ emailVerified: false });
		setAuthenticated(user);
		userState.set(user.member);
		householdState.set(user.household);

		// when
		render(SettingsUserPage);

		// then
		await expect.element(page.getByLabelText('E-Mail-Adresse')).toHaveAttribute('disabled');
		await expect.element(page.getByText('um sie ändern zu können')).toBeVisible();
		await expect.element(page.getByText('Passwort ändern zu können')).toBeVisible();
		await expect.element(page.getByRole('button', { name: 'Passwort ändern' })).toBeDisabled();
	});

	it('emailVerified true — leaves the email input and password form enabled', async () => {
		// given
		const user = currentUser({ emailVerified: true });
		setAuthenticated(user);
		userState.set(user.member);
		householdState.set(user.household);

		// when
		render(SettingsUserPage);

		// then
		await expect.element(page.getByLabelText('E-Mail-Adresse')).not.toHaveAttribute('disabled');
	});

	it('saveProfile while unverified — omits the email field from the update request', async () => {
		// given
		const user = currentUser({ emailVerified: false });
		setAuthenticated(user);
		userState.set(user.member);
		householdState.set(user.household);
		mockUpdateMember.mockResolvedValue(undefined);
		render(SettingsUserPage);

		// when
		await page.getByLabelText('Anzeigename').fill('Neuer Name');
		await page.getByRole('button', { name: 'Speichern' }).click();

		// then
		await vi.waitFor(() =>
			expect(mockUpdateMember).toHaveBeenCalledWith({
				householdId: 'household-1',
				memberId: 'member-1',
				memberUpdate: { name: 'Neuer Name' }
			})
		);
	});
});
