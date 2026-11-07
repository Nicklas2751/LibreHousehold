import { apiConfiguration } from '#lib/api/httpClient.ts';
import { SessionApi } from '../../generated-sources/openapi';

const sessionApi = new SessionApi(apiConfiguration);

export async function logout(): Promise<void> {
	try {
		await sessionApi.logout();
	} catch {
		// The full reload below resets the SPA either way, so a failed request must not leave the
		// user signed in on screen; POST /logout is idempotent and can simply be repeated.
	}
	// A full reload instead of goto(): it clears every in-memory store (relevant on shared
	// devices) and avoids racing the app guard and the WelcomeScreen redirect.
	window.location.replace('/');
}
