import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import LoginPage from './+page.svelte';
import { authDiscovery } from '$lib/stores/authDiscoveryState.svelte';

const { mockGoto, mockBootstrapSession, mockLoadAuthDiscovery, mockStartSocialLogin, mockPage } =
	vi.hoisted(() => ({
		mockGoto: vi.fn(),
		mockBootstrapSession: vi.fn(),
		mockLoadAuthDiscovery: vi.fn(),
		mockStartSocialLogin: vi.fn(),
		mockPage: { url: new URL('http://localhost:5173/login') }
	}));

vi.mock('$app/navigation', () => ({ goto: mockGoto }));

vi.mock('$app/state', () => ({ page: mockPage }));

vi.mock('$lib/stores/authDiscoveryState.svelte', async (importOriginal) => {
	const original = await importOriginal<typeof import('$lib/stores/authDiscoveryState.svelte')>();
	return { ...original, loadAuthDiscovery: mockLoadAuthDiscovery };
});

vi.mock('$lib/socialLogin', () => ({ startSocialLogin: mockStartSocialLogin }));

vi.mock('$lib/stores/sessionBootstrap', () => ({ bootstrapSession: mockBootstrapSession }));

vi.mock('../../lib/paraglide/runtime.js', async (importOriginal) => {
	const original = await importOriginal<typeof import('../../lib/paraglide/runtime.js')>();
	return { ...original, getLocale: () => 'de' as const, setLocale: vi.fn() };
});

function clearCookies() {
	document.cookie.split(';').forEach((cookie) => {
		const name = cookie.split('=')[0].trim();
		if (name) {
			document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
		}
	});
}

async function fillAndSubmit() {
	render(LoginPage);

	await page.getByRole('textbox', { name: /E-Mail/i }).fill('max@example.com');
	await page.getByLabelText(/Passwort/i).fill('supersecret');
	await page.getByRole('button', { name: /Anmelden/i }).click();
}

describe('login page', () => {
	beforeEach(() => {
		vi.clearAllMocks();
		clearCookies();
		document.cookie = 'XSRF-TOKEN=test-csrf-token';
		mockPage.url = new URL('http://localhost:5173/login');
		mockLoadAuthDiscovery.mockResolvedValue(undefined);
		authDiscovery.localEnabled = true;
		authDiscovery.socialProviders = [];
	});

	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it('successful login — posts to /login with the correct body and CSRF header, then navigates to the dashboard', async () => {
		// given
		const mockFetch = vi
			.fn()
			.mockResolvedValue({ url: 'http://localhost:5173/app/dashboard' } as Response);
		vi.stubGlobal('fetch', mockFetch);
		mockBootstrapSession.mockResolvedValue(undefined);

		// when
		await fillAndSubmit();

		// then
		await vi.waitFor(() => expect(mockGoto).toHaveBeenCalledWith('/app/dashboard'));
		expect(mockFetch).toHaveBeenCalledWith(
			'/login',
			expect.objectContaining({
				method: 'POST',
				credentials: 'include',
				headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'test-csrf-token' }),
				body: new URLSearchParams({ username: 'max@example.com', password: 'supersecret' })
			})
		);
		expect(mockBootstrapSession).toHaveBeenCalled();
	});

	it('failed login (wrong password) — shows an error message and does not navigate', async () => {
		// given
		const mockFetch = vi
			.fn()
			.mockResolvedValue({ url: 'http://localhost:5173/login?error' } as Response);
		vi.stubGlobal('fetch', mockFetch);

		// when
		await fillAndSubmit();

		// then
		await expect.element(page.getByText('falsch')).toBeVisible();
		expect(mockGoto).not.toHaveBeenCalled();
		expect(mockBootstrapSession).not.toHaveBeenCalled();
	});

	it('locked account — shows the lockout message with the formatted unlock time', async () => {
		// given
		const lockedUntil = new Date(Date.now() + 15 * 60 * 1000);
		const mockFetch = vi.fn().mockResolvedValue({
			url: `http://localhost:5173/login?error&reason=locked&lockedUntil=${encodeURIComponent(lockedUntil.toISOString())}`
		} as Response);
		vi.stubGlobal('fetch', mockFetch);

		// when
		await fillAndSubmit();

		// then
		await expect.element(page.getByText(lockedUntil.toLocaleTimeString())).toBeVisible();
		expect(mockGoto).not.toHaveBeenCalled();
		expect(mockBootstrapSession).not.toHaveBeenCalled();
	});

	it('locked account without a lockedUntil value — shows the generic lockout message', async () => {
		// given
		const mockFetch = vi
			.fn()
			.mockResolvedValue({ url: 'http://localhost:5173/login?error&reason=locked' } as Response);
		vi.stubGlobal('fetch', mockFetch);

		// when
		await fillAndSubmit();

		// then
		await expect.element(page.getByText('später erneut')).toBeVisible();
		expect(mockGoto).not.toHaveBeenCalled();
	});

	it('network error — shows an error message', async () => {
		// given
		const mockFetch = vi.fn().mockRejectedValue(new Error('network error'));
		vi.stubGlobal('fetch', mockFetch);

		// when
		await fillAndSubmit();

		// then
		await expect.element(page.getByText('falsch')).toBeVisible();
		expect(mockGoto).not.toHaveBeenCalled();
	});

	it('on mount — loads the available login methods', async () => {
		// when
		render(LoginPage);

		// then
		await vi.waitFor(() => expect(mockLoadAuthDiscovery).toHaveBeenCalled());
	});

	it('local login disabled — hides the e-mail and password form', async () => {
		// given
		authDiscovery.localEnabled = false;
		authDiscovery.socialProviders = [{ id: 'google', displayName: 'Google' }];

		// when
		render(LoginPage);

		// then
		await expect.element(page.getByRole('textbox', { name: /E-Mail/i })).not.toBeInTheDocument();
	});

	it('social provider button — starts the social login with the LOGIN context', async () => {
		// given
		authDiscovery.socialProviders = [{ id: 'google', displayName: 'Google' }];
		render(LoginPage);

		// when
		await page.getByRole('button', { name: 'Weiter mit Google' }).click();

		// then
		expect(mockStartSocialLogin).toHaveBeenCalledWith('google', { context: 'LOGIN' });
	});

	it('back button — links to the start page', async () => {
		// when
		render(LoginPage);

		// then
		await expect.element(page.getByRole('link', { name: 'Zurück' })).toHaveAttribute('href', '/');
	});

	it.each([
		{ reason: 'social-login-no-account', expectedText: 'kein Konto in LibreHousehold' },
		{ reason: 'social-login-failed', expectedText: 'über den Anbieter ist fehlgeschlagen' },
		{ reason: 'local-login-disabled', expectedText: 'nur über einen Anbieter' }
	])(
		'redirected back with reason $reason — shows the matching error message',
		async ({ reason, expectedText }) => {
			// given
			mockPage.url = new URL(`http://localhost:5173/login?error&reason=${reason}`);

			// when
			render(LoginPage);

			// then
			await expect.element(page.getByText(expectedText)).toBeVisible();
		}
	);
});
