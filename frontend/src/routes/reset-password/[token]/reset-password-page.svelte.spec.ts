import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import ResetPasswordPage from './+page.svelte';

const { mockConfirmPasswordReset, mockGoto, mockAddToast } = vi.hoisted(() => ({
	mockConfirmPasswordReset: vi.fn(),
	mockGoto: vi.fn(),
	mockAddToast: vi.fn()
}));

vi.mock('$lib/stores/passwordReset', () => ({
	confirmPasswordReset: mockConfirmPasswordReset
}));

vi.mock('$app/navigation', () => ({ goto: mockGoto }));

vi.mock('$lib/stores/toastStore', () => ({ addToast: mockAddToast }));

vi.mock('$app/stores', () => ({
	page: {
		subscribe: (run: (value: { params: { token: string } }) => void) => {
			run({ params: { token: 'test-token' } });
			return () => {};
		}
	}
}));

vi.mock('$lib/paraglide/runtime.js', async (importOriginal) => {
	const original = await importOriginal<typeof import('$lib/paraglide/runtime.js')>();
	return { ...original, getLocale: () => 'de' as const, setLocale: vi.fn() };
});

async function fillAndSubmit() {
	render(ResetPasswordPage);
	await page.getByLabelText('Neues Passwort').fill('newSecurePassword123');
	await page.getByRole('button', { name: 'Passwort zurücksetzen' }).click();
}

describe('reset-password page', () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	it('ruft confirmPasswordReset mit Token aus der URL und eingegebenem Passwort auf und navigiert zum Login', async () => {
		// given
		mockConfirmPasswordReset.mockResolvedValue(undefined);

		// when
		await fillAndSubmit();

		// then
		await vi.waitFor(() =>
			expect(mockConfirmPasswordReset).toHaveBeenCalledWith('test-token', 'newSecurePassword123')
		);
		await vi.waitFor(() => expect(mockGoto).toHaveBeenCalledWith('/login'));
		expect(mockAddToast).toHaveBeenCalled();
	});

	it('zeigt einen Fehlerzustand und einen Link zurück zu forgot-password bei ungültigem Token (409)', async () => {
		// given
		mockConfirmPasswordReset.mockRejectedValue({ status: 409 });

		// when
		await fillAndSubmit();

		// then
		await expect.element(page.getByRole('link', { name: 'Neuen Link anfordern' })).toBeVisible();
		expect(mockGoto).not.toHaveBeenCalled();
	});
});
