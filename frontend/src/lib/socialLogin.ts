import { m } from '$lib/paraglide/messages.js';
import { Toast } from '$lib/toast';
import { addToast } from '$lib/stores/toastStore';
import { apiConfiguration } from '$lib/api/httpClient';
import { type SocialLoginContext, SessionApi } from '../generated-sources/openapi';

const OAUTH2_AUTHORIZATION_PATH = '/oauth2/authorization/';

const sessionApi = new SessionApi(apiConfiguration);

// The server needs to know why the provider redirect was started (login, setup or invite join)
// when the provider redirects back, so the context goes into the session before leaving the SPA.
export async function startSocialLogin(
	registrationId: string,
	socialLoginContext: SocialLoginContext
): Promise<void> {
	try {
		await sessionApi.storeSocialLoginContext({ socialLoginContext });
	} catch {
		addToast(new Toast(m['auth.social_login_start_error'](), 'error'));
		return;
	}
	// A top-level navigation, not fetch(): the provider's login page has to open in the browser.
	window.location.href = OAUTH2_AUTHORIZATION_PATH + registrationId;
}
