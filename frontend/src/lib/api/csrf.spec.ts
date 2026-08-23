import { describe, expect, it } from 'vitest';
import { getCsrfTokenFromCookieHeader, isStateChangingMethod } from './csrf';

describe('getCsrfTokenFromCookieHeader', () => {
	it('liest den XSRF-TOKEN-Wert aus einem Cookie-String mit mehreren Cookies', () => {
		// given
		const cookieHeader = 'SESSION=abc123; XSRF-TOKEN=token-value; other=value';

		// when
		const result = getCsrfTokenFromCookieHeader(cookieHeader);

		// then
		expect(result).toBe('token-value');
	});

	it('gibt null zurück, wenn kein XSRF-TOKEN-Cookie vorhanden ist', () => {
		// given
		const cookieHeader = 'SESSION=abc123; other=value';

		// when
		const result = getCsrfTokenFromCookieHeader(cookieHeader);

		// then
		expect(result).toBeNull();
	});

	it('dekodiert einen URL-kodierten Cookie-Wert', () => {
		// given
		const cookieHeader = 'XSRF-TOKEN=token%2Fwith%2Fslashes';

		// when
		const result = getCsrfTokenFromCookieHeader(cookieHeader);

		// then
		expect(result).toBe('token/with/slashes');
	});
});

describe('isStateChangingMethod', () => {
	it.each(['POST', 'PUT', 'PATCH', 'DELETE'])('gibt true für %s zurück', (method) => {
		// when
		const result = isStateChangingMethod(method);

		// then
		expect(result).toBe(true);
	});

	it.each(['GET', 'HEAD', 'OPTIONS'])('gibt false für %s zurück', (method) => {
		// when
		const result = isStateChangingMethod(method);

		// then
		expect(result).toBe(false);
	});
});
