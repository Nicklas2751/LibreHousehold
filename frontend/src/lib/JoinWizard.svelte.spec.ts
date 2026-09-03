import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import JoinWizard from './JoinWizard.svelte';

const {
	mockResolveInvite,
	mockJoinHousehold,
	mockCheckEmailAvailability,
	mockGoto,
	mockAddToast,
	mockCompleteSilentOAuth2Login,
	mockBootstrapSession
} = vi.hoisted(() => ({
	mockResolveInvite: vi.fn(),
	mockJoinHousehold: vi.fn(),
	mockCheckEmailAvailability: vi.fn(),
	mockGoto: vi.fn(),
	mockAddToast: vi.fn(),
	mockCompleteSilentOAuth2Login: vi.fn(),
	mockBootstrapSession: vi.fn()
}));

vi.mock('../generated-sources/openapi', async (importOriginal) => {
	const original = await importOriginal<typeof import('../generated-sources/openapi')>();
	return {
		...original,
		MembersApi: vi.fn().mockImplementation(function (this: Record<string, unknown>) {
			this.resolveInvite = mockResolveInvite;
			this.joinHousehold = mockJoinHousehold;
			this.checkEmailAvailability = mockCheckEmailAvailability;
		})
	};
});

vi.mock('$app/navigation', () => ({ goto: mockGoto }));

vi.mock('$lib/stores/toastStore', () => ({ addToast: mockAddToast }));

vi.mock('$lib/oauth2Login', async (importOriginal) => {
	const original = await importOriginal<typeof import('$lib/oauth2Login')>();
	return { ...original, completeSilentOAuth2Login: mockCompleteSilentOAuth2Login };
});

vi.mock('$lib/stores/sessionBootstrap', () => ({ bootstrapSession: mockBootstrapSession }));

vi.mock('./paraglide/runtime.js', async (importOriginal) => {
	const original = await importOriginal<typeof import('./paraglide/runtime.js')>();
	return { ...original, getLocale: () => 'de' as const, setLocale: vi.fn() };
});

const validInviteInfo = {
	householdId: '11111111-1111-1111-1111-111111111111',
	householdName: 'Die Müllers',
	validUntil: new Date('2099-12-31')
};

describe('JoinWizard', () => {
	beforeEach(() => {
		vi.clearAllMocks();
		mockCheckEmailAvailability.mockResolvedValue({ available: true });
		mockCompleteSilentOAuth2Login.mockResolvedValue(undefined);
		mockBootstrapSession.mockResolvedValue(undefined);
	});

	it('invalid token — shows an error message', async () => {
		// given
		mockResolveInvite.mockRejectedValue({ status: 404 });

		// when
		render(JoinWizard, { token: '00000000-0000-0000-0000-000000000000' });

		// then
		await expect.element(page.getByText('ungültig oder abgelaufen')).toBeVisible();
	});

	it('valid token — shows the household name in the form', async () => {
		// given
		mockResolveInvite.mockResolvedValue(validInviteInfo);

		// when
		render(JoinWizard, { token: 'abc-token' });

		// then
		await expect.element(page.getByText('Die Müllers')).toBeVisible();
	});

	it('submits the form — calls joinHousehold with the correct data', async () => {
		// given
		mockResolveInvite.mockResolvedValue(validInviteInfo);
		mockJoinHousehold.mockResolvedValue({
			id: 'member-id',
			name: 'Max Mustermann',
			email: 'max@example.com',
			isAdmin: false
		});
		render(JoinWizard, { token: 'valid-token' });

		// when
		await page.getByRole('textbox', { name: /Dein Name/i }).fill('Max Mustermann');
		await page.getByRole('textbox', { name: /Deine E-Mail/i }).fill('max@example.com');
		await page.getByLabelText(/Passwort/i).fill('supersecret');
		await page.getByRole('button', { name: /Beitreten/i }).click();

		// then
		expect(mockJoinHousehold).toHaveBeenCalledWith(
			expect.objectContaining({
				token: 'valid-token',
				memberRegistration: expect.objectContaining({
					name: 'Max Mustermann',
					email: 'max@example.com',
					localRegistration: { password: 'supersecret' }
				})
			})
		);
	});

	it('successful join — shows a success message and navigates to the dashboard', async () => {
		// given
		mockResolveInvite.mockResolvedValue(validInviteInfo);
		mockJoinHousehold.mockResolvedValue({
			id: 'member-id',
			name: 'Max Mustermann',
			email: 'max@example.com',
			isAdmin: false
		});
		render(JoinWizard, { token: 'valid-token' });

		// when
		await page.getByRole('textbox', { name: /Dein Name/i }).fill('Max Mustermann');
		await page.getByRole('textbox', { name: /Deine E-Mail/i }).fill('max@example.com');
		await page.getByLabelText(/Passwort/i).fill('supersecret');
		await page.getByRole('button', { name: /Beitreten/i }).click();

		// then
		await expect.element(page.getByText('Erfolgreich beigetreten')).toBeVisible();
		await page.getByRole('button', { name: /Zum Dashboard/i }).click();
		await vi.waitFor(() => expect(mockGoto).toHaveBeenCalledWith('/app/dashboard'));
		expect(mockCompleteSilentOAuth2Login).toHaveBeenCalled();
		expect(mockBootstrapSession).toHaveBeenCalled();
	});

	it('409 account-already-exists — shows a specific error on the email field', async () => {
		// given
		mockResolveInvite.mockResolvedValue(validInviteInfo);
		mockJoinHousehold.mockRejectedValue(
			await import('../generated-sources/openapi').then(
				({ ResponseError }) =>
					new ResponseError(
						new Response(JSON.stringify({ type: '/problems/account-already-exists' }), {
							status: 409
						})
					)
			)
		);
		render(JoinWizard, { token: 'valid-token' });

		// when
		await page.getByRole('textbox', { name: /Dein Name/i }).fill('Max Mustermann');
		await page.getByRole('textbox', { name: /Deine E-Mail/i }).fill('taken@example.com');
		await page.getByLabelText(/Passwort/i).fill('supersecret');
		await page.getByRole('button', { name: /Beitreten/i }).click();

		// then
		await expect.element(page.getByText('bereits registriert')).toBeVisible();
	});

	it('409 with an invalid response body — falls back to the generic join-error toast', async () => {
		// given
		mockResolveInvite.mockResolvedValue(validInviteInfo);
		mockJoinHousehold.mockRejectedValue(
			await import('../generated-sources/openapi').then(
				({ ResponseError }) => new ResponseError(new Response('not valid json{{{', { status: 409 }))
			)
		);
		render(JoinWizard, { token: 'valid-token' });

		// when
		await page.getByRole('textbox', { name: /Dein Name/i }).fill('Max Mustermann');
		await page.getByRole('textbox', { name: /Deine E-Mail/i }).fill('max@example.com');
		await page.getByLabelText(/Passwort/i).fill('supersecret');
		await page.getByRole('button', { name: /Beitreten/i }).click();

		// then
		await vi.waitFor(() =>
			expect(mockAddToast).toHaveBeenCalledWith(
				expect.objectContaining({ message: expect.stringContaining('Fehler beim Beitreten') })
			)
		);
	});

	it('email availability check — calls checkEmailAvailability after the debounce time', async () => {
		// given
		mockResolveInvite.mockResolvedValue(validInviteInfo);
		render(JoinWizard, { token: 'valid-token' });

		// when
		await page.getByRole('textbox', { name: /Deine E-Mail/i }).fill('max@example.com');

		// then
		await vi.waitFor(
			() => expect(mockCheckEmailAvailability).toHaveBeenCalledWith({ email: 'max@example.com' }),
			{ timeout: 2000 }
		);
	});

	it('incomplete email — does not call checkEmailAvailability', async () => {
		// given
		mockResolveInvite.mockResolvedValue(validInviteInfo);
		render(JoinWizard, { token: 'valid-token' });

		// when
		await page.getByRole('textbox', { name: /Deine E-Mail/i }).fill('max');
		await new Promise((resolve) => setTimeout(resolve, 600));

		// then
		expect(mockCheckEmailAvailability).not.toHaveBeenCalled();
	});
});
