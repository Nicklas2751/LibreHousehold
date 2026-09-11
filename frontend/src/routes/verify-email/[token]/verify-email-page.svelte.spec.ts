import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import VerifyEmailPage from './+page.svelte';

const { mockConfirmEmailVerification } = vi.hoisted(() => ({
	mockConfirmEmailVerification: vi.fn()
}));

vi.mock('$lib/stores/memberStore', () => ({
	confirmEmailVerification: mockConfirmEmailVerification
}));

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

describe('verify-email page', () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	it('ruft confirmEmailVerification mit dem Token aus der URL auf', async () => {
		// given
		mockConfirmEmailVerification.mockResolvedValue(undefined);

		// when
		render(VerifyEmailPage);

		// then
		await vi.waitFor(() => expect(mockConfirmEmailVerification).toHaveBeenCalledWith('test-token'));
		await expect.element(page.getByRole('heading', { name: 'E-Mail bestätigt!' })).toBeVisible();
	});

	it('zeigt einen Fehlerzustand bei ungültigem/abgelaufenem Token (409)', async () => {
		// given
		mockConfirmEmailVerification.mockRejectedValue(new Error('conflict'));

		// when
		render(VerifyEmailPage);

		// then
		await expect
			.element(page.getByRole('heading', { name: 'Bestätigung fehlgeschlagen' }))
			.toBeVisible();
	});
});
