import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import ForgotPasswordPage from './+page.svelte';
import { authDiscovery } from '$lib/stores/authDiscoveryState.svelte';

const { mockRequestPasswordReset, mockLoadAuthDiscovery } = vi.hoisted(() => ({
	mockRequestPasswordReset: vi.fn(),
	mockLoadAuthDiscovery: vi.fn()
}));

vi.mock('$lib/stores/authDiscoveryState.svelte', async (importOriginal) => {
	const original = await importOriginal<typeof import('$lib/stores/authDiscoveryState.svelte')>();
	return { ...original, loadAuthDiscovery: mockLoadAuthDiscovery };
});

vi.mock('$lib/stores/passwordReset', () => ({
	requestPasswordReset: mockRequestPasswordReset
}));

vi.mock('$lib/paraglide/runtime.js', async (importOriginal) => {
	const original = await importOriginal<typeof import('$lib/paraglide/runtime.js')>();
	return { ...original, getLocale: () => 'de' as const, setLocale: vi.fn() };
});

async function fillAndSubmit() {
	render(ForgotPasswordPage);
	await page.getByRole('textbox', { name: 'E-Mail' }).fill('max@example.com');
	await page.getByRole('button', { name: 'Link anfordern' }).click();
}

describe('forgot-password page', () => {
	beforeEach(() => {
		vi.clearAllMocks();
		mockLoadAuthDiscovery.mockResolvedValue(undefined);
		authDiscovery.localEnabled = true;
		authDiscovery.socialProviders = [];
	});

	it('on mount — loads the available login methods', async () => {
		// when
		render(ForgotPasswordPage);

		// then
		await vi.waitFor(() => expect(mockLoadAuthDiscovery).toHaveBeenCalled());
	});

	it('success with configured providers — shows the social hint with the provider names', async () => {
		// given
		authDiscovery.socialProviders = [
			{ id: 'google', displayName: 'Google' },
			{ id: 'github', displayName: 'GitHub' }
		];
		mockRequestPasswordReset.mockResolvedValue(undefined);

		// when
		await fillAndSubmit();

		// then
		await expect
			.element(page.getByText('Hast du dich über einen Anbieter registriert'))
			.toMatchTextContent('„Weiter mit Google / GitHub“');
	});

	it('success without providers — shows no social hint', async () => {
		// given
		mockRequestPasswordReset.mockResolvedValue(undefined);

		// when
		await fillAndSubmit();
		await expect.element(page.getByText('wurde eine E-Mail zum Zurücksetzen')).toBeVisible();

		// then
		await expect
			.element(page.getByText('Hast du dich über einen Anbieter registriert'))
			.not.toBeInTheDocument();
	});

	it('ruft requestPasswordReset mit der eingegebenen E-Mail auf', async () => {
		// given
		mockRequestPasswordReset.mockResolvedValue(undefined);

		// when
		await fillAndSubmit();

		// then
		await vi.waitFor(() =>
			expect(mockRequestPasswordReset).toHaveBeenCalledWith('max@example.com')
		);
		await expect.element(page.getByText('wurde eine E-Mail zum Zurücksetzen')).toBeVisible();
	});

	it('zeigt die generische Erfolgsmeldung auch bei einem Fehler-Response an', async () => {
		// given
		mockRequestPasswordReset.mockRejectedValue(new Error('network error'));

		// when
		await fillAndSubmit();

		// then
		await expect.element(page.getByText('wurde eine E-Mail zum Zurücksetzen')).toBeVisible();
	});
});
