import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from 'vitest-browser-svelte';
import { page } from 'vitest/browser';
import SocialLoginButtons from './SocialLoginButtons.svelte';
import { authDiscovery } from '$lib/stores/authDiscoveryState.svelte';
import { SocialLoginContextContextEnum } from '../generated-sources/openapi';

const { mockStartSocialLogin } = vi.hoisted(() => ({
	mockStartSocialLogin: vi.fn()
}));

vi.mock('$lib/socialLogin', () => ({ startSocialLogin: mockStartSocialLogin }));

vi.mock('./paraglide/runtime.js', async (importOriginal) => {
	const original = await importOriginal<typeof import('./paraglide/runtime.js')>();
	return { ...original, getLocale: () => 'de' as const, setLocale: vi.fn() };
});

describe('SocialLoginButtons', () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	it('renders one button per configured social provider', async () => {
		// given
		authDiscovery.localEnabled = true;
		authDiscovery.socialProviders = [
			{ id: 'google', displayName: 'Google' },
			{ id: 'github', displayName: 'GitHub' }
		];

		// when
		render(SocialLoginButtons, {
			socialLoginContext: { context: SocialLoginContextContextEnum.Login }
		});

		// then
		await expect.element(page.getByRole('button', { name: 'Weiter mit Google' })).toBeVisible();
		await expect.element(page.getByRole('button', { name: 'Weiter mit GitHub' })).toBeVisible();
	});

	it('starts the social login with the provider id and the given context on click', async () => {
		// given
		authDiscovery.localEnabled = true;
		authDiscovery.socialProviders = [{ id: 'google', displayName: 'Google' }];
		const socialLoginContext = {
			context: SocialLoginContextContextEnum.Invite,
			inviteToken: 'invite-token'
		};
		render(SocialLoginButtons, { socialLoginContext });

		// when
		await page.getByRole('button', { name: 'Weiter mit Google' }).click();

		// then
		expect(mockStartSocialLogin).toHaveBeenCalledWith('google', socialLoginContext);
	});

	it('calls onbeforestart before starting the social login', async () => {
		// given
		authDiscovery.localEnabled = true;
		authDiscovery.socialProviders = [{ id: 'google', displayName: 'Google' }];
		const onbeforestart = vi.fn();
		render(SocialLoginButtons, {
			socialLoginContext: { context: SocialLoginContextContextEnum.Setup },
			onbeforestart
		});

		// when
		await page.getByRole('button', { name: 'Weiter mit Google' }).click();

		// then
		expect(onbeforestart.mock.invocationCallOrder[0]).toBeLessThan(
			mockStartSocialLogin.mock.invocationCallOrder[0]
		);
	});

	it('renders no divider when local login is disabled', async () => {
		// given
		authDiscovery.localEnabled = false;
		authDiscovery.socialProviders = [{ id: 'google', displayName: 'Google' }];

		// when
		render(SocialLoginButtons, {
			socialLoginContext: { context: SocialLoginContextContextEnum.Login }
		});

		// then
		await expect.element(page.getByText('oder', { exact: true })).not.toBeInTheDocument();
	});

	it('renders nothing when no social provider is configured', async () => {
		// given
		authDiscovery.localEnabled = true;
		authDiscovery.socialProviders = [];

		// when
		const { container } = render(SocialLoginButtons, {
			socialLoginContext: { context: SocialLoginContextContextEnum.Login }
		});

		// then
		expect(container.textContent?.trim()).toBe('');
	});
});
