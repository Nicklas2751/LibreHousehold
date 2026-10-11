import { describe, it, expect, vi, beforeEach } from 'vitest';
import { startSocialLogin } from './socialLogin';
import {
	type SocialLoginContext,
	SocialLoginContextContextEnum
} from '../generated-sources/openapi';

const { mockStoreSocialLoginContext, mockRedirectToOAuth2Login, mockAddToast } = vi.hoisted(() => ({
	mockStoreSocialLoginContext: vi.fn(),
	mockRedirectToOAuth2Login: vi.fn(),
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

vi.mock('$lib/oauth2Login', () => ({ redirectToOAuth2Login: mockRedirectToOAuth2Login }));

vi.mock('$lib/stores/toastStore', () => ({ addToast: mockAddToast }));

describe('startSocialLogin', () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	it.each<SocialLoginContext>([
		{ context: SocialLoginContextContextEnum.Login },
		{ context: SocialLoginContextContextEnum.Setup },
		{ context: SocialLoginContextContextEnum.Invite, inviteToken: 'invite-token' }
	])('startSocialLogin_context $context_storesContextInSession', async (socialLoginContext) => {
		// given
		mockStoreSocialLoginContext.mockResolvedValue(undefined);

		// when
		await startSocialLogin('google', socialLoginContext);

		// then
		expect(mockStoreSocialLoginContext).toHaveBeenCalledWith({ socialLoginContext });
	});

	it('startSocialLogin_contextStored_redirectsToProviderAfterStoring', async () => {
		// given
		mockStoreSocialLoginContext.mockResolvedValue(undefined);

		// when
		await startSocialLogin('google', { context: SocialLoginContextContextEnum.Login });

		// then
		expect(mockRedirectToOAuth2Login).toHaveBeenCalledWith('google');
		expect(mockStoreSocialLoginContext.mock.invocationCallOrder[0]).toBeLessThan(
			mockRedirectToOAuth2Login.mock.invocationCallOrder[0]
		);
	});

	it('startSocialLogin_storingContextFails_showsErrorToastWithoutRedirect', async () => {
		// given
		mockStoreSocialLoginContext.mockRejectedValue(new Error('network down'));

		// when
		await startSocialLogin('google', { context: SocialLoginContextContextEnum.Login });

		// then
		expect({
			toastShown: mockAddToast.mock.calls.length,
			redirected: mockRedirectToOAuth2Login.mock.calls.length
		}).toEqual({ toastShown: 1, redirected: 0 });
	});
});
