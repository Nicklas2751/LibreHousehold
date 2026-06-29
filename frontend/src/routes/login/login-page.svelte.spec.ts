import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';

const mockGetAuthProviders = vi.hoisted(() => vi.fn());
const mockLogin = vi.hoisted(() => vi.fn().mockResolvedValue(undefined));
const mockSearchParamsGet = vi.hoisted(() => vi.fn().mockReturnValue(null));
const mockFetch = vi.hoisted(() => vi.fn());

vi.mock('../../generated-sources/openapi', async (importOriginal) => {
	const original = await importOriginal<typeof import('../../generated-sources/openapi')>();
	return {
		...original,
		AuthApi: vi.fn().mockImplementation(function (this: Record<string, unknown>) {
			this.getAuthProviders = mockGetAuthProviders;
		})
	};
});

vi.mock('$lib/stores/authStore.svelte', () => ({
	login: mockLogin,
	isAuthenticated: {
		subscribe: (run: (v: boolean) => void) => {
			run(false);
			return () => {};
		}
	}
}));

vi.mock('$app/state', () => ({
	page: {
		url: { searchParams: { get: mockSearchParamsGet } }
	}
}));

import Page from './+page.svelte';

describe('Login Page', () => {
	beforeEach(() => {
		vi.clearAllMocks();
		mockSearchParamsGet.mockReturnValue(null);
		sessionStorage.setItem('lh_pkce_started', '1');
	});

	afterEach(() => {
		sessionStorage.removeItem('lh_pkce_started');
		vi.unstubAllGlobals();
	});

	it('withLocalProvider_showsEmailAndPasswordFields', async () => {
		// given
		mockGetAuthProviders.mockResolvedValue({ local: true, socialProviders: [] });

		// when
		render(Page);

		// then
		await expect.element(page.getByRole('textbox', { name: 'Email address' })).toBeVisible();
		await expect.element(page.getByLabelText('Password')).toBeVisible();
	});

	it('withSocialProvider_showsSocialLoginButton', async () => {
		// given
		mockGetAuthProviders.mockResolvedValue({ local: false, socialProviders: ['google'] });

		// when
		render(Page);

		// then
		await expect.element(page.getByRole('link', { name: /Google/i })).toBeVisible();
	});

	it('withNoProviders_showsNoProvidersWarning', async () => {
		// given
		mockGetAuthProviders.mockResolvedValue({ local: false, socialProviders: [] });

		// when
		render(Page);

		// then
		await expect.element(page.getByText(/No sign-in method configured/i)).toBeVisible();
	});

	it('withBadCredentialsError_displaysErrorMessage', async () => {
		// given
		mockSearchParamsGet.mockImplementation((key: string) => (key === 'error' ? 'bad_credentials' : null));
		mockGetAuthProviders.mockResolvedValue({ local: true, socialProviders: [] });

		// when
		render(Page);

		// then
		await expect.element(page.getByText(/Invalid email address or password/i)).toBeVisible();
	});

	it('submitForm_postsToApiLogin', async () => {
		// given
		mockGetAuthProviders.mockResolvedValue({ local: true, socialProviders: [] });
		vi.stubGlobal('fetch', mockFetch.mockResolvedValue(new Response()));
		render(Page);
		await expect.element(page.getByRole('textbox', { name: 'Email address' })).toBeVisible();

		// when
		await page.getByRole('textbox', { name: 'Email address' }).fill('test@example.com');
		await page.getByLabelText('Password').fill('secret123');
		await page.getByRole('button', { name: 'Sign In' }).click();

		// then
		expect(mockFetch).toHaveBeenCalledWith(
			'/api/login',
			expect.objectContaining({ method: 'POST' })
		);
	});

	it('submitForm_sendsXsrfTokenHeader', async () => {
		// given
		mockGetAuthProviders.mockResolvedValue({ local: true, socialProviders: [] });
		document.cookie = 'XSRF-TOKEN=test-csrf-token';
		vi.stubGlobal('fetch', mockFetch.mockResolvedValue(new Response()));
		render(Page);
		await expect.element(page.getByRole('textbox', { name: 'Email address' })).toBeVisible();

		// when
		await page.getByRole('textbox', { name: 'Email address' }).fill('test@example.com');
		await page.getByLabelText('Password').fill('secret123');
		await page.getByRole('button', { name: 'Sign In' }).click();

		// then
		expect(mockFetch).toHaveBeenCalledWith(
			'/api/login',
			expect.objectContaining({
				headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'test-csrf-token' })
			})
		);
	});
});
