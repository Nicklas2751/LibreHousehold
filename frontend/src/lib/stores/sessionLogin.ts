import { apiConfiguration } from '#lib/api/httpClient.ts';
import { getCsrfTokenFromCookieHeader } from '#lib/api/csrf.ts';
import { SessionApi } from '../../generated-sources/openapi';

const sessionApi = new SessionApi(apiConfiguration);

// POST /login needs the X-XSRF-TOKEN header like every state-changing request. Without an
// XSRF-TOKEN cookie (e.g. on a fresh visit) any GET makes the backend issue one first.
export async function loginWithPassword(email: string, password: string): Promise<void> {
	if (!getCsrfTokenFromCookieHeader(document.cookie)) {
		await sessionApi.getAuthDiscovery();
	}
	await sessionApi.login({ email, password });
}
