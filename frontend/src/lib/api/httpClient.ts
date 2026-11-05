import { Configuration, type Middleware } from '../../generated-sources/openapi';
import { getCsrfTokenFromCookieHeader, isStateChangingMethod } from './csrf';
import { session, setGuest } from '../stores/sessionState.svelte';
import { shouldTreatAsSessionExpiry } from '../stores/sessionGuard';

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
		// The app guard reacts to the guest status and navigates to /login.
		setGuest();
	}
};

export const apiConfiguration = new Configuration({
	basePath: '/api',
	credentials: 'include',
	middleware: [csrfMiddleware, sessionExpiredMiddleware]
});
