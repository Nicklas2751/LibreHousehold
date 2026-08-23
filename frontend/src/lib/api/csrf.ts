const XSRF_TOKEN_COOKIE_NAME = 'XSRF-TOKEN';
const STATE_CHANGING_METHODS = ['POST', 'PUT', 'PATCH', 'DELETE'];

export function getCsrfTokenFromCookieHeader(cookieHeader: string): string | null {
	const cookiePrefix = `${XSRF_TOKEN_COOKIE_NAME}=`;
	const cookie = cookieHeader.split('; ').find((entry) => entry.startsWith(cookiePrefix));
	if (!cookie) {
		return null;
	}
	return decodeURIComponent(cookie.slice(cookiePrefix.length));
}

export function isStateChangingMethod(method: string): boolean {
	return STATE_CHANGING_METHODS.includes(method.toUpperCase());
}
