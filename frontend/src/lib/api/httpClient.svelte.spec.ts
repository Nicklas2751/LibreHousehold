import { beforeEach, describe, expect, it } from 'vitest';
import { apiConfiguration, csrfMiddleware } from './httpClient';

function clearCookies() {
	document.cookie.split(';').forEach((cookie) => {
		const name = cookie.split('=')[0].trim();
		if (name) {
			document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
		}
	});
}

describe('csrfMiddleware', () => {
	beforeEach(() => {
		clearCookies();
	});

	it('setzt den X-XSRF-TOKEN-Header bei POST, wenn ein XSRF-TOKEN-Cookie gesetzt ist', async () => {
		// given
		document.cookie = 'XSRF-TOKEN=csrf-token-value';

		// when
		const result = await csrfMiddleware.pre?.({
			fetch,
			url: '/api/some-resource',
			init: { method: 'POST', headers: {} }
		});

		// then
		expect(result?.init.headers).toEqual(
			expect.objectContaining({ 'X-XSRF-TOKEN': 'csrf-token-value' })
		);
	});

	it('setzt keinen Header bei GET, selbst wenn ein XSRF-TOKEN-Cookie gesetzt ist', async () => {
		// given
		document.cookie = 'XSRF-TOKEN=csrf-token-value';

		// when
		const result = await csrfMiddleware.pre?.({
			fetch,
			url: '/api/some-resource',
			init: { method: 'GET', headers: {} }
		});

		// then
		expect(result).toBeUndefined();
	});

	it('setzt keinen Header, wenn kein XSRF-TOKEN-Cookie vorhanden ist', async () => {
		// given (kein Cookie gesetzt)

		// when
		const result = await csrfMiddleware.pre?.({
			fetch,
			url: '/api/some-resource',
			init: { method: 'POST', headers: {} }
		});

		// then
		expect(result).toBeUndefined();
	});
});

describe('apiConfiguration', () => {
	it('sendet Requests mit credentials include', () => {
		// when
		const result = apiConfiguration.credentials;

		// then
		expect(result).toBe('include');
	});

	it('registriert die csrfMiddleware', () => {
		// when
		const result = apiConfiguration.middleware;

		// then
		expect(result).toContain(csrfMiddleware);
	});
});
