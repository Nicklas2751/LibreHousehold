import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import SetupWizard from './SetupWizard.svelte';
import { authDiscovery } from '$lib/stores/authDiscoveryState.svelte';

const {
	mockSetupHousehold,
	mockCheckEmailAvailability,
	mockGoto,
	mockAddToast,
	mockCompleteSilentOAuth2Login,
	mockBootstrapSession,
	mockLoadAuthDiscovery,
	mockStartSocialLogin
} = vi.hoisted(() => ({
	mockSetupHousehold: vi.fn(),
	mockCheckEmailAvailability: vi.fn(),
	mockGoto: vi.fn(),
	mockAddToast: vi.fn(),
	mockCompleteSilentOAuth2Login: vi.fn(),
	mockBootstrapSession: vi.fn(),
	mockLoadAuthDiscovery: vi.fn(),
	mockStartSocialLogin: vi.fn()
}));

vi.mock('../generated-sources/openapi', async (importOriginal) => {
	const original = await importOriginal<typeof import('../generated-sources/openapi')>();
	return {
		...original,
		HouseholdApi: vi.fn().mockImplementation(function (this: Record<string, unknown>) {
			this.setupHousehold = mockSetupHousehold;
		}),
		MembersApi: vi.fn().mockImplementation(function (this: Record<string, unknown>) {
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

vi.mock('$lib/stores/authDiscoveryState.svelte', async (importOriginal) => {
	const original = await importOriginal<typeof import('$lib/stores/authDiscoveryState.svelte')>();
	return { ...original, loadAuthDiscovery: mockLoadAuthDiscovery };
});

vi.mock('$lib/socialLogin', () => ({ startSocialLogin: mockStartSocialLogin }));

vi.mock('./paraglide/runtime.js', async (importOriginal) => {
	const original = await importOriginal<typeof import('./paraglide/runtime.js')>();
	return { ...original, getLocale: () => 'de' as const, setLocale: vi.fn() };
});

async function fillAndGoToAccountStep() {
	render(SetupWizard);

	await page.getByPlaceholder(/Die Müllers/i).fill('Die Testfamilie');
	await page.getByRole('button', { name: /Weiter/i }).click();

	await expect.element(page.getByRole('heading', { name: /Richte dein Konto ein/i })).toBeVisible();
}

describe('SetupWizard', () => {
	beforeEach(() => {
		vi.clearAllMocks();
		mockCheckEmailAvailability.mockResolvedValue({ available: true });
		mockCompleteSilentOAuth2Login.mockResolvedValue(undefined);
		mockBootstrapSession.mockResolvedValue(undefined);
		mockLoadAuthDiscovery.mockResolvedValue(undefined);
		authDiscovery.localEnabled = true;
		authDiscovery.socialProviders = [];
	});

	it('submits the form — calls setupHousehold with localRegistration.password', async () => {
		// given
		mockSetupHousehold.mockResolvedValue({
			household: { id: 'household-id', name: 'Die Testfamilie' },
			inviteToken: 'invite-token'
		});
		await fillAndGoToAccountStep();

		// when
		await page.getByRole('textbox', { name: /Dein Name/i }).fill('Max Mustermann');
		await page.getByRole('textbox', { name: /Deine E-Mail/i }).fill('max@example.com');
		await page.getByLabelText(/Passwort/i).fill('supersecret');
		await page.getByRole('button', { name: /Einrichtung abschließen/i }).click();

		// then
		await vi.waitFor(() => expect(mockSetupHousehold).toHaveBeenCalled());
		expect(mockSetupHousehold).toHaveBeenCalledWith(
			expect.objectContaining({
				householdSetup: expect.objectContaining({
					localRegistration: { password: 'supersecret' }
				})
			})
		);
	});

	it('closing setup after successful setup — completes the OAuth2 login silently and navigates to the dashboard', async () => {
		// given
		mockSetupHousehold.mockResolvedValue({
			household: { id: 'household-id', name: 'Die Testfamilie' },
			inviteToken: 'invite-token'
		});
		await fillAndGoToAccountStep();
		await page.getByRole('textbox', { name: /Dein Name/i }).fill('Max Mustermann');
		await page.getByRole('textbox', { name: /Deine E-Mail/i }).fill('max@example.com');
		await page.getByLabelText(/Passwort/i).fill('supersecret');
		await page.getByRole('button', { name: /Einrichtung abschließen/i }).click();
		await expect.element(page.getByRole('button', { name: /Zum Dashboard gehen/i })).toBeVisible();

		// when
		await page.getByRole('button', { name: /Zum Dashboard gehen/i }).click();

		// then
		await vi.waitFor(() => expect(mockGoto).toHaveBeenCalledWith('/app/dashboard'));
		expect(mockCompleteSilentOAuth2Login).toHaveBeenCalled();
		expect(mockBootstrapSession).toHaveBeenCalled();
	});

	it('email already taken (account-already-exists) — shows a specific error on the email field', async () => {
		// given
		mockSetupHousehold.mockRejectedValue(
			await import('../generated-sources/openapi').then(
				({ ResponseError }) =>
					new ResponseError(
						new Response(JSON.stringify({ type: '/problems/account-already-exists' }), {
							status: 409
						})
					)
			)
		);
		await fillAndGoToAccountStep();

		// when
		await page.getByRole('textbox', { name: /Dein Name/i }).fill('Max Mustermann');
		await page.getByRole('textbox', { name: /Deine E-Mail/i }).fill('taken@example.com');
		await page.getByLabelText(/Passwort/i).fill('supersecret');
		await page.getByRole('button', { name: /Einrichtung abschließen/i }).click();

		// then
		await expect.element(page.getByText('bereits registriert')).toBeVisible();
	});

	it('household already exists (household-already-exists) — shows a generic error toast', async () => {
		// given
		mockSetupHousehold.mockRejectedValue(
			await import('../generated-sources/openapi').then(
				({ ResponseError }) =>
					new ResponseError(
						new Response(JSON.stringify({ type: '/problems/household-already-exists' }), {
							status: 409
						})
					)
			)
		);
		await fillAndGoToAccountStep();

		// when
		await page.getByRole('textbox', { name: /Dein Name/i }).fill('Max Mustermann');
		await page.getByRole('textbox', { name: /Deine E-Mail/i }).fill('max@example.com');
		await page.getByLabelText(/Passwort/i).fill('supersecret');
		await page.getByRole('button', { name: /Einrichtung abschließen/i }).click();

		// then
		await vi.waitFor(() =>
			expect(mockAddToast).toHaveBeenCalledWith(
				expect.objectContaining({ message: expect.stringContaining('existiert bereits ein Konto') })
			)
		);
	});

	it('409 with an invalid response body — still shows the generic error toast', async () => {
		// given
		mockSetupHousehold.mockRejectedValue(
			await import('../generated-sources/openapi').then(
				({ ResponseError }) => new ResponseError(new Response('not valid json{{{', { status: 409 }))
			)
		);
		await fillAndGoToAccountStep();

		// when
		await page.getByRole('textbox', { name: /Dein Name/i }).fill('Max Mustermann');
		await page.getByRole('textbox', { name: /Deine E-Mail/i }).fill('max@example.com');
		await page.getByLabelText(/Passwort/i).fill('supersecret');
		await page.getByRole('button', { name: /Einrichtung abschließen/i }).click();

		// then
		await vi.waitFor(() =>
			expect(mockAddToast).toHaveBeenCalledWith(
				expect.objectContaining({ message: expect.stringContaining('fehlgeschlagen') })
			)
		);
	});

	it('password too short — submit button is disabled and setupHousehold is not called', async () => {
		// given
		await fillAndGoToAccountStep();

		// when
		await page.getByRole('textbox', { name: /Dein Name/i }).fill('Max Mustermann');
		await page.getByRole('textbox', { name: /Deine E-Mail/i }).fill('max@example.com');
		await page.getByLabelText(/Passwort/i).fill('short');

		// then
		await expect
			.element(page.getByRole('button', { name: /Einrichtung abschließen/i }))
			.toBeDisabled();
		expect(mockSetupHousehold).not.toHaveBeenCalled();
	});

	it('email availability check — calls checkEmailAvailability after the debounce time', async () => {
		// given
		mockCheckEmailAvailability.mockResolvedValue({ available: true });
		await fillAndGoToAccountStep();

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
		mockCheckEmailAvailability.mockResolvedValue({ available: true });
		await fillAndGoToAccountStep();

		// when
		await page.getByRole('textbox', { name: /Deine E-Mail/i }).fill('max');
		await new Promise((resolve) => setTimeout(resolve, 600));

		// then
		expect(mockCheckEmailAvailability).not.toHaveBeenCalled();
	});

	it('on mount — loads the available login methods', async () => {
		// when
		render(SetupWizard);

		// then
		await vi.waitFor(() => expect(mockLoadAuthDiscovery).toHaveBeenCalled());
	});

	it('account step with a social provider — starts the social login with the SETUP context', async () => {
		// given
		authDiscovery.socialProviders = [{ id: 'google', displayName: 'Google' }];
		await fillAndGoToAccountStep();

		// when
		await page.getByRole('button', { name: 'Weiter mit Google' }).click();

		// then
		expect(mockStartSocialLogin).toHaveBeenCalledWith('google', { context: 'SETUP' });
	});

	it('account step with local login disabled — hides the account form', async () => {
		// given
		authDiscovery.localEnabled = false;
		authDiscovery.socialProviders = [{ id: 'google', displayName: 'Google' }];

		// when
		await fillAndGoToAccountStep();

		// then
		await expect.element(page.getByRole('textbox', { name: /Dein Name/i })).not.toBeInTheDocument();
	});
});
