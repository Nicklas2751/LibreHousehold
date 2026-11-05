import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { startSocialLogin } from './socialLogin';
import {
	type SocialLoginContext,
	SocialLoginContextContextEnum
} from '../generated-sources/openapi';

const { mockStoreSocialLoginContext, mockAddToast } = vi.hoisted(() => ({
	mockStoreSocialLoginContext: vi.fn(),
	mockAddToast: vi.fn()
}));

vi.mock('../generated-sources/openapi', async (importOriginal) => {
	const original = await importOriginal<typeof import('../generated-sources/openapi')>();
	return {
		...original,
		SessionApi: vi.fn().mockImplementation(function (this: Record<string, unknown>) {
			this.storeSocialLoginContext = mockStoreSocialLoginContext;
		})
	};
});

vi.mock('$lib/stores/toastStore', () => ({ addToast: mockAddToast }));

describe('startSocialLogin', () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it.each<SocialLoginContext>([
		{ context: SocialLoginContextContextEnum.Login },
		{ context: SocialLoginContextContextEnum.Setup },
		{ context: SocialLoginContextContextEnum.Invite, inviteToken: 'invite-token' }
	])('startSocialLogin_context $context_storesContextInSession', async (socialLoginContext) => {
		// given
		mockStoreSocialLoginContext.mockResolvedValue(undefined);
		vi.stubGlobal('window', { location: { href: '' } });

		// when
		await startSocialLogin('google', socialLoginContext);

		// then
		expect(mockStoreSocialLoginContext).toHaveBeenCalledWith({ socialLoginContext });
	});

	it('startSocialLogin_contextStored_redirectsToProviderAfterStoring', async () => {
		// given
		const fakeWindow = { location: { href: '' } };
		vi.stubGlobal('window', fakeWindow);
		const hrefWhileStoring: string[] = [];
		mockStoreSocialLoginContext.mockImplementation(async () =>
			hrefWhileStoring.push(fakeWindow.location.href)
		);

		// when
		await startSocialLogin('google', { context: SocialLoginContextContextEnum.Login });

		// then
		expect({ hrefWhileStoring, hrefAfterwards: fakeWindow.location.href }).toEqual({
			hrefWhileStoring: [''],
			hrefAfterwards: '/oauth2/authorization/google'
		});
	});

	it('startSocialLogin_storingContextFails_showsErrorToastWithoutRedirect', async () => {
		// given
		mockStoreSocialLoginContext.mockRejectedValue(new Error('network down'));
		// Paraglide resolves the locale from the window URL while rendering the toast message.
		const fakeWindow = { location: { href: 'http://localhost/login', origin: 'http://localhost' } };
		vi.stubGlobal('window', fakeWindow);

		// when
		await startSocialLogin('google', { context: SocialLoginContextContextEnum.Login });

		// then
		expect({
			toastShown: mockAddToast.mock.calls.length,
			href: fakeWindow.location.href
		}).toEqual({ toastShown: 1, href: 'http://localhost/login' });
	});
});
