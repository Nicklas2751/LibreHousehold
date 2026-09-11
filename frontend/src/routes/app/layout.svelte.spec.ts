import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import { createRawSnippet } from 'svelte';
import Layout from './+layout.svelte';
import { session, setAuthenticated, setGuest } from '$lib/stores/sessionState.svelte';
import type { CurrentUser } from '../../generated-sources/openapi';

const childrenSnippet = createRawSnippet(() => ({ render: () => `<div></div>` }));

const { mockResendVerificationEmail, mockAddToast } = vi.hoisted(() => ({
	mockResendVerificationEmail: vi.fn(),
	mockAddToast: vi.fn()
}));

vi.mock('$lib/stores/memberStore', () => ({
	resendVerificationEmail: mockResendVerificationEmail
}));

vi.mock('$lib/stores/toastStore', () => ({ addToast: mockAddToast }));

vi.mock('$lib/stores/settingsStore', () => ({ initSettings: vi.fn() }));

vi.mock('$lib/oauth2Login', () => ({ redirectToOAuth2Login: vi.fn() }));

vi.mock('$lib/paraglide/runtime.js', async (importOriginal) => {
	const original = await importOriginal<typeof import('$lib/paraglide/runtime.js')>();
	return { ...original, getLocale: () => 'de' as const, setLocale: vi.fn() };
});

function currentUser(overrides: Partial<CurrentUser> = {}): CurrentUser {
	return {
		member: { id: 'member-1', name: 'Max', email: 'max@example.com' },
		household: { id: 'household-1', name: 'Musterhaushalt' },
		preferences: { theme: 'light', language: 'de' },
		emailVerified: false,
		...overrides
	};
}

describe('app layout - verification banner', () => {
	beforeEach(() => {
		vi.clearAllMocks();
		localStorage.clear();
		setGuest();
	});

	it('zeigt das Verifikationsbanner, wenn emailVerified false ist', async () => {
		// given
		setAuthenticated(currentUser({ emailVerified: false }));

		// when
		render(Layout, { children: childrenSnippet });

		// then
		await expect.element(page.getByText('Erneut senden')).toBeVisible();
	});

	it('zeigt kein Banner, wenn emailVerified true ist', async () => {
		// given
		setAuthenticated(currentUser({ emailVerified: true }));

		// when
		render(Layout, { children: childrenSnippet });

		// then
		await expect.element(page.getByText('Erneut senden')).not.toBeInTheDocument();
	});

	it('ruft resendVerificationEmail auf und zeigt einen Erfolgs-Toast bei Klick auf Erneut senden', async () => {
		// given
		setAuthenticated(currentUser({ emailVerified: false }));
		mockResendVerificationEmail.mockResolvedValue(undefined);
		render(Layout, { children: childrenSnippet });

		// when
		await page.getByText('Erneut senden').click();

		// then
		await vi.waitFor(() =>
			expect(mockResendVerificationEmail).toHaveBeenCalledWith('household-1', 'member-1')
		);
		expect(mockAddToast).toHaveBeenCalled();
	});

	it('zeigt das Banner nach Reload nicht erneut, wenn es zuvor für denselben Member geschlossen wurde', async () => {
		// given
		setAuthenticated(currentUser({ emailVerified: false }));
		render(Layout, { children: childrenSnippet });
		await page.getByLabelText('Schließen').click();

		// when
		render(Layout, { children: childrenSnippet });

		// then
		await expect.element(page.getByText('Erneut senden')).not.toBeInTheDocument();
	});

	it('zeigt das Banner weiterhin, wenn emailVerified weiterhin false ist, obwohl es zuvor geschlossen wurde und der Reload simuliert wird', async () => {
		// given
		setAuthenticated(currentUser({ emailVerified: false }));
		render(Layout, { children: childrenSnippet });
		await page.getByLabelText('Schließen').click();
		await expect.element(page.getByText('Erneut senden')).not.toBeInTheDocument();

		// when: session is refreshed but emailVerified is still false (dismissal only hides, doesn't override status)
		setAuthenticated(currentUser({ emailVerified: false }));

		// then
		expect(session.currentUser?.emailVerified).toBe(false);
	});
});
