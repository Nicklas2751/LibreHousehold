import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import ForgotPasswordPage from './+page.svelte';

const { mockRequestPasswordReset } = vi.hoisted(() => ({
	mockRequestPasswordReset: vi.fn()
}));

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
