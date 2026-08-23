import { Configuration, type Middleware } from '../../generated-sources/openapi';
import { getCsrfTokenFromCookieHeader, isStateChangingMethod } from './csrf';

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

export const apiConfiguration = new Configuration({
	basePath: '/api',
	credentials: 'include',
	middleware: [csrfMiddleware]
});
