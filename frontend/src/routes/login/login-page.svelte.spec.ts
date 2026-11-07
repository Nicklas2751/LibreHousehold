import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import LoginPage from './+page.svelte';
import { authDiscovery } from '#lib/stores/authDiscoveryState.svelte.ts';
import { setAuthenticated, setGuest } from '#lib/stores/sessionState.svelte.ts';

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

vi.mock('#lib/stores/authDiscoveryState.svelte.ts', async (importOriginal) => {
	const original =
		await importOriginal<typeof import('#lib/stores/authDiscoveryState.svelte.ts')>();
	return { ...original, loadAuthDiscovery: mockLoadAuthDiscovery };
});

vi.mock('#lib/socialLogin.ts', () => ({ startSocialLogin: mockStartSocialLogin }));

vi.mock('#lib/stores/sessionBootstrap.ts', () => ({ bootstrapSession: mockBootstrapSession }));

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

const currentUser = {
	member: { id: 'member-id', name: 'Max Mustermann', email: 'max@example.com', isAdmin: true },
	household: { id: 'household-id', name: 'Die Testfamilie' },
	preferences: {},
	emailVerified: true,
	socialLoginOnly: false,
	sessionTimeoutSeconds: 1800
};

function problemResponse(status: number, body: object, headers: Record<string, string> = {}) {
	return new Response(JSON.stringify(body), {
		status,
		headers: { 'Content-Type': 'application/problem+json', ...headers }
	});
}

function requestedUrls(mockFetch: ReturnType<typeof vi.fn>): string[] {
	return mockFetch.mock.calls.map(([url]) => String(url));
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
		setGuest();
		document.cookie = 'XSRF-TOKEN=test-csrf-token';
		mockPage.url = new URL('http://localhost:5173/login');
		mockLoadAuthDiscovery.mockResolvedValue(undefined);
		authDiscovery.localEnabled = true;
		authDiscovery.socialProviders = [];
	});

	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it('success_postsCredentialsWithCsrfHeader', async () => {
		// given
		const mockFetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
		vi.stubGlobal('fetch', mockFetch);

		// when
		await fillAndSubmit();

		// then
		await vi.waitFor(() =>
			expect(mockFetch).toHaveBeenCalledWith(
				'/api/login',
				expect.objectContaining({
					method: 'POST',
					headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'test-csrf-token' }),
					body: new URLSearchParams({ email: 'max@example.com', password: 'supersecret' })
				})
			)
		);
	});

	it('success_navigatesToDashboardWithReplaceState', async () => {
		// given
		vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
		mockBootstrapSession.mockImplementation(async () => setAuthenticated(currentUser));

		// when
		await fillAndSubmit();

		// then
		await vi.waitFor(() =>
			expect(mockGoto).toHaveBeenCalledWith('/app/dashboard', { replace: true })
		);
	});

	it('success_navigatesToSafeReturnTo', async () => {
		// given
		mockPage.url = new URL('http://localhost:5173/login?returnTo=%2Fapp%2Ftasks%3Ffilter%3Dopen');
		vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
		mockBootstrapSession.mockImplementation(async () => setAuthenticated(currentUser));

		// when
		await fillAndSubmit();

		// then
		await vi.waitFor(() =>
			expect(mockGoto).toHaveBeenCalledWith('/app/tasks?filter=open', { replace: true })
		);
	});

	it('successWithUnsafeReturnTo_navigatesToDashboard', async () => {
		// given
		mockPage.url = new URL('http://localhost:5173/login?returnTo=%2F%2Fevil.example');
		vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
		mockBootstrapSession.mockImplementation(async () => setAuthenticated(currentUser));

		// when
		await fillAndSubmit();

		// then
		await vi.waitFor(() =>
			expect(mockGoto).toHaveBeenCalledWith('/app/dashboard', { replace: true })
		);
	});

	it('sessionExpiredReason_showsInfo', async () => {
		// given
		mockPage.url = new URL(
			'http://localhost:5173/login?returnTo=%2Fapp%2Ftasks&reason=session-expired'
		);

		// when
		render(LoginPage);

		// then
		await expect.element(page.getByText('Deine Sitzung ist abgelaufen')).toBeVisible();
	});

	it('error_alertFocused', async () => {
		// given
		vi.stubGlobal(
			'fetch',
			vi.fn().mockResolvedValue(problemResponse(401, { type: '/problems/invalid-credentials' }))
		);

		// when
		await fillAndSubmit();

		// then
		await expect.element(page.getByRole('alert')).toHaveFocus();
	});

	it('invalidCredentialsWithProviders_showsSocialHintWithProviderNames', async () => {
		// given
		authDiscovery.socialProviders = [
			{ id: 'google', displayName: 'Google' },
			{ id: 'github', displayName: 'GitHub' }
		];
		vi.stubGlobal(
			'fetch',
			vi.fn().mockResolvedValue(problemResponse(401, { type: '/problems/invalid-credentials' }))
		);

		// when
		await fillAndSubmit();

		// then
		await expect
			.element(page.getByRole('alert'))
			.toMatchTextContent(
				'Falls du dich über einen Anbieter registriert hast, nutze „Weiter mit Google / GitHub“.'
			);
	});

	it('invalidCredentials_doesNotNavigate', async () => {
		// given
		vi.stubGlobal(
			'fetch',
			vi.fn().mockResolvedValue(problemResponse(401, { type: '/problems/invalid-credentials' }))
		);

		// when
		await fillAndSubmit();

		// then
		await expect.element(page.getByText('E-Mail oder Passwort falsch')).toBeVisible();
		expect({
			navigated: mockGoto.mock.calls.length,
			bootstrapped: mockBootstrapSession.mock.calls.length
		}).toEqual({ navigated: 0, bootstrapped: 0 });
	});

	it('unverified_showsUnverifiedMessage', async () => {
		// given
		vi.stubGlobal(
			'fetch',
			vi.fn().mockResolvedValue(problemResponse(403, { type: '/problems/email-not-verified' }))
		);

		// when
		await fillAndSubmit();

		// then
		await expect.element(page.getByText('bestätige zuerst deine E-Mail-Adresse')).toBeVisible();
	});

	it('localLoginDisabled_showsLocalLoginDisabledMessage', async () => {
		// given
		vi.stubGlobal(
			'fetch',
			vi.fn().mockResolvedValue(problemResponse(403, { type: '/problems/local-login-disabled' }))
		);

		// when
		await fillAndSubmit();

		// then
		await expect.element(page.getByText('nur über einen Anbieter')).toBeVisible();
	});

	it.each([
		{ lockedUntil: new Date(Date.now() + 15 * 60 * 1000).toISOString() },
		{ lockedUntil: undefined }
	])('locked_showsResetLink (lockedUntil $lockedUntil)', async ({ lockedUntil }) => {
		// given
		vi.stubGlobal(
			'fetch',
			vi
				.fn()
				.mockResolvedValue(problemResponse(403, { type: '/problems/account-locked', lockedUntil }))
		);

		// when
		await fillAndSubmit();

		// then
		await expect
			.element(page.getByRole('alert').getByRole('link'))
			.toHaveAttribute('href', '/forgot-password');
	});

	it('locked_showsFormattedUnlockTime', async () => {
		// given
		const lockedUntil = new Date(Date.now() + 15 * 60 * 1000);
		vi.stubGlobal(
			'fetch',
			vi
				.fn()
				.mockResolvedValue(
					problemResponse(
						403,
						{ type: '/problems/account-locked', lockedUntil: lockedUntil.toISOString() },
						{ 'Retry-After': '900' }
					)
				)
		);

		// when
		await fillAndSubmit();

		// then
		await expect
			.element(
				page.getByText(new Intl.DateTimeFormat('de', { timeStyle: 'short' }).format(lockedUntil))
			)
			.toBeVisible();
	});

	it('locked_doesNotNavigate', async () => {
		// given
		vi.stubGlobal(
			'fetch',
			vi.fn().mockResolvedValue(problemResponse(403, { type: '/problems/account-locked' }))
		);

		// when
		await fillAndSubmit();

		// then
		await expect.element(page.getByText('später erneut')).toBeVisible();
		expect({
			navigated: mockGoto.mock.calls.length,
			bootstrapped: mockBootstrapSession.mock.calls.length
		}).toEqual({ navigated: 0, bootstrapped: 0 });
	});

	it('rateLimited_doesNotNavigate', async () => {
		// given
		vi.stubGlobal(
			'fetch',
			vi
				.fn()
				.mockResolvedValue(
					problemResponse(429, { type: '/problems/too-many-requests' }, { 'Retry-After': '42' })
				)
		);

		// when
		await fillAndSubmit();

		// then
		await expect.element(page.getByText('Zu viele Anmeldeversuche')).toBeVisible();
		expect({
			navigated: mockGoto.mock.calls.length,
			bootstrapped: mockBootstrapSession.mock.calls.length
		}).toEqual({ navigated: 0, bootstrapped: 0 });
	});

	it('rateLimited_showsRetryAfterSeconds', async () => {
		// given
		vi.stubGlobal(
			'fetch',
			vi
				.fn()
				.mockResolvedValue(
					problemResponse(429, { type: '/problems/too-many-requests' }, { 'Retry-After': '42' })
				)
		);

		// when
		await fillAndSubmit();

		// then
		await expect.element(page.getByText('42 Sekunden')).toBeVisible();
	});

	it('rateLimitedWithoutRetryAfter_showsFallback', async () => {
		// given
		vi.stubGlobal(
			'fetch',
			vi.fn().mockResolvedValue(problemResponse(429, { type: '/problems/too-many-requests' }))
		);

		// when
		await fillAndSubmit();

		// then
		await expect.element(page.getByText('Bitte warte einen Moment')).toBeVisible();
	});

	it('network_showsNetworkMessage', async () => {
		// given
		vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

		// when
		await fillAndSubmit();

		// then
		await expect.element(page.getByText('Keine Verbindung zum Server')).toBeVisible();
	});

	it('unexpected_showsUnexpectedMessage', async () => {
		// given
		vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 500 })));

		// when
		await fillAndSubmit();

		// then
		await expect.element(page.getByText('Etwas ist schiefgelaufen')).toBeVisible();
	});

	it('missingXsrfCookie_fetchesDiscoverFirst', async () => {
		// given
		clearCookies();
		const mockFetch = vi
			.fn()
			.mockImplementation(async (url: string) =>
				url === '/api/auth/discover'
					? Response.json({ localEnabled: true, socialProviders: [] })
					: new Response(null, { status: 204 })
			);
		vi.stubGlobal('fetch', mockFetch);

		// when
		await fillAndSubmit();

		// then
		await vi.waitFor(() =>
			expect(requestedUrls(mockFetch)).toEqual(['/api/auth/discover', '/api/login'])
		);
	});

	it('existingXsrfCookie_postsLoginWithoutDiscover', async () => {
		// given
		const mockFetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
		vi.stubGlobal('fetch', mockFetch);

		// when
		await fillAndSubmit();

		// then
		await vi.waitFor(() => expect(requestedUrls(mockFetch)).toEqual(['/api/login']));
	});

	it('authenticated_redirectsToDashboard', async () => {
		// given
		setAuthenticated(currentUser);

		// when
		render(LoginPage);

		// then
		await vi.waitFor(() =>
			expect(mockGoto).toHaveBeenCalledWith('/app/dashboard', { replace: true })
		);
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

	it('redirected back with reason social-login-no-account and local login disabled — asks for an invitation only', async () => {
		// given
		authDiscovery.localEnabled = false;
		mockPage.url = new URL('http://localhost:5173/login?error&reason=social-login-no-account');

		// when
		render(LoginPage);

		// then
		await expect
			.element(page.getByRole('alert'))
			.toHaveTextContent(
				'Für dieses Anbieter-Konto gibt es kein LibreHousehold-Konto. Lass dich in einen Haushalt einladen.'
			);
	});

	it.each([
		{ reason: 'social-login-no-account', expectedText: 'kein Konto in LibreHousehold' },
		{ reason: 'social-login-failed', expectedText: 'über den Anbieter ist fehlgeschlagen' },
		{ reason: 'some-unknown-reason', expectedText: 'Etwas ist schiefgelaufen' }
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
