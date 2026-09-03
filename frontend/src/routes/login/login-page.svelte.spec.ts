import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import LoginPage from './+page.svelte';

const { mockGoto, mockBootstrapSession } = vi.hoisted(() => ({
	mockGoto: vi.fn(),
	mockBootstrapSession: vi.fn()
}));

vi.mock('$app/navigation', () => ({ goto: mockGoto }));

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
});
