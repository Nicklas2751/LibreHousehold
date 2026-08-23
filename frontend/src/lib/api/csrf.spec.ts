import { describe, expect, it } from 'vitest';
import { getCsrfTokenFromCookieHeader, isStateChangingMethod } from './csrf';

describe('getCsrfTokenFromCookieHeader', () => {
	it('reads the XSRF-TOKEN value from a cookie string with multiple cookies', () => {
		// given
		const cookieHeader = 'SESSION=abc123; XSRF-TOKEN=token-value; other=value';

		// when
		const result = getCsrfTokenFromCookieHeader(cookieHeader);

		// then
		expect(result).toBe('token-value');
	});

	it('returns null when no XSRF-TOKEN cookie is present', () => {
		// given
		const cookieHeader = 'SESSION=abc123; other=value';

		// when
		const result = getCsrfTokenFromCookieHeader(cookieHeader);

		// then
		expect(result).toBeNull();
	});

	it('decodes a URL-encoded cookie value', () => {
		// given
		const cookieHeader = 'XSRF-TOKEN=token%2Fwith%2Fslashes';

		// when
		const result = getCsrfTokenFromCookieHeader(cookieHeader);

		// then
		expect(result).toBe('token/with/slashes');
	});
});

describe('isStateChangingMethod', () => {
	it.each(['POST', 'PUT', 'PATCH', 'DELETE'])('returns true for %s', (method) => {
		// when
		const result = isStateChangingMethod(method);

		// then
		expect(result).toBe(true);
	});

	it.each(['GET', 'HEAD', 'OPTIONS'])('returns false for %s', (method) => {
		// when
		const result = isStateChangingMethod(method);

		// then
		expect(result).toBe(false);
	});
});
