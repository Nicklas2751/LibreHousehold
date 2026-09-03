import { Configuration, type Middleware } from '../../generated-sources/openapi';
import { getCsrfTokenFromCookieHeader, isStateChangingMethod } from './csrf';
import { session, setGuest } from '../stores/sessionState.svelte';
import { shouldTreatAsSessionExpiry } from '../stores/sessionGuard';
import { redirectToOAuth2Login } from '../oauth2Login';

const HTTP_STATUS_UNAUTHORIZED = 401;

export const csrfMiddleware: Middleware = {
	pre: async ({ url, init }) => {
		if (!isStateChangingMethod(init.method ?? 'GET')) {
			return;
		}
		const token = getCsrfTokenFromCookieHeader(document.cookie);
		if (!token) {
			return;
		}
		return {
			url,
			init: { ...init, headers: { ...init.headers, 'X-XSRF-TOKEN': token } }
		};
	}
};

export const sessionExpiredMiddleware: Middleware = {
	post: async ({ response }) => {
		if (response.status !== HTTP_STATUS_UNAUTHORIZED) {
			return;
		}
		if (!shouldTreatAsSessionExpiry(session.status)) {
			return;
		}
		setGuest();
		// A real navigation (not goto) always reloads the page, so a second call before the
		// browser has actually left is harmless — no duplicate-navigation guard needed here.
		redirectToOAuth2Login();
	}
};

export const apiConfiguration = new Configuration({
	basePath: '/api',
	credentials: 'include',
	middleware: [csrfMiddleware, sessionExpiredMiddleware]
});
