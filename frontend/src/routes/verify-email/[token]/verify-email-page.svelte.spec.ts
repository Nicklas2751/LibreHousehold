import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import VerifyEmailPage from './+page.svelte';
import { setAuthenticated, setGuest } from '$lib/stores/sessionState.svelte';
import type { CurrentUser } from '../../../generated-sources/openapi';

const { mockConfirmEmailVerification, mockBootstrapSession } = vi.hoisted(() => ({
	mockConfirmEmailVerification: vi.fn(),
	mockBootstrapSession: vi.fn()
}));

vi.mock('$lib/stores/memberStore', () => ({
	confirmEmailVerification: mockConfirmEmailVerification
}));

vi.mock('$lib/stores/sessionBootstrap', () => ({ bootstrapSession: mockBootstrapSession }));

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

function currentUser(): CurrentUser {
	return {
		member: { id: 'member-1', name: 'Max', email: 'max@example.com', isAdmin: false },
		household: { id: 'household-1', name: 'Musterhaushalt' },
		preferences: { theme: 'light', language: 'de' },
		emailVerified: false
	};
}

describe('verify-email page', () => {
	beforeEach(() => {
		vi.clearAllMocks();
		setGuest();
	});

	it('ruft confirmEmailVerification mit dem Token aus der URL auf und aktualisiert danach die Session', async () => {
		// given
		mockConfirmEmailVerification.mockResolvedValue(undefined);
		mockBootstrapSession.mockResolvedValue(undefined);

		// when
		render(VerifyEmailPage);

		// then
		await vi.waitFor(() => expect(mockConfirmEmailVerification).toHaveBeenCalledWith('test-token'));
		await expect.element(page.getByRole('heading', { name: 'E-Mail bestätigt!' })).toBeVisible();
		expect(mockBootstrapSession).toHaveBeenCalledWith({ silent: true });
	});

	it('zeigt "Zum Dashboard", wenn noch eine authentifizierte Session besteht', async () => {
		// given
		setAuthenticated(currentUser());
		mockConfirmEmailVerification.mockResolvedValue(undefined);
		mockBootstrapSession.mockResolvedValue(undefined);

		// when
		render(VerifyEmailPage);

		// then
		await expect
			.element(page.getByRole('link', { name: 'Zum Dashboard' }))
			.toHaveAttribute('href', '/app/dashboard');
	});

	it('zeigt "Zum Login", wenn keine authentifizierte Session besteht', async () => {
		// given
		setGuest();
		mockConfirmEmailVerification.mockResolvedValue(undefined);
		mockBootstrapSession.mockResolvedValue(undefined);

		// when
		render(VerifyEmailPage);

		// then
		await expect
			.element(page.getByRole('link', { name: 'Zum Login' }))
			.toHaveAttribute('href', '/login');
	});

	it('zeigt einen Fehlerzustand bei ungültigem/abgelaufenem Token (409) ohne die Session zu aktualisieren', async () => {
		// given
		mockConfirmEmailVerification.mockRejectedValue(new Error('conflict'));

		// when
		render(VerifyEmailPage);

		// then
		await expect
			.element(page.getByRole('heading', { name: 'Bestätigung fehlgeschlagen' }))
			.toBeVisible();
		expect(mockBootstrapSession).not.toHaveBeenCalled();
	});
});
